package com.calyvora.people;

import com.calyvora.common.config.BackgroundWorkConfig;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.common.security.TenantFilter;
import com.calyvora.people.dto.PayrollJobResponse;
import com.calyvora.people.dto.PayrollRunResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A payroll run as a job: started with one request, finished in the background, read back by id.
 *
 * <p>The run was a single GET that computed every payslip before answering. Fine at seven people;
 * at a thousand it held a request open for seconds, and on a host that cuts long requests it
 * would one day answer nothing at all, after doing all the work. Starting it and polling means
 * the request returns at once, the page can say what it is waiting for, and the number of
 * simultaneous runs is bounded by a pool rather than by how many tabs someone opened.
 *
 * <p>In memory, on purpose. A run is a computation over data that is already durable; losing the
 * job on a restart costs one click. Persisting it would buy a table, a migration, a cleanup
 * schedule and a tenant policy for something that lives ten seconds. Finished jobs are kept for an
 * hour so a page reload can find its result, then dropped.
 *
 * <p>Jobs are keyed by tenant as well as id, so a job id — a UUID anyone could guess at — reads as
 * "not found" from any other company. Same shape as every other lookup here.
 */
@Service
public class PayrollJobService {

    private static final Logger log = LoggerFactory.getLogger(PayrollJobService.class);
    private static final Duration KEEP_FINISHED = Duration.ofHours(1);

    private final CompensationService compensationService;
    private final Map<UUID, Job> jobs = new ConcurrentHashMap<>();

    public PayrollJobService(CompensationService compensationService) {
        this.compensationService = compensationService;
    }

    /** One job. Mutable status, guarded by the map's happens-before on put/get. */
    static final class Job {
        final UUID id;
        final UUID companyId;
        final String month;
        final Instant startedAt = Instant.now();
        volatile Instant finishedAt;
        volatile PayrollRunResponse result;
        volatile String error;

        Job(UUID id, UUID companyId, String month) {
            this.id = id;
            this.companyId = companyId;
            this.month = month;
        }

        String status() {
            if (finishedAt == null) return "RUNNING";
            return error == null ? "DONE" : "FAILED";
        }
    }

    /**
     * Start a run for the month, or hand back the one already running for it. Two people in HR
     * pressing the same button should share a computation, not double the load.
     */
    public PayrollJobResponse start(String month) {
        UUID companyId = TenantContext.getCompanyId();
        String ym = normalise(month);
        evictStale();

        for (Job existing : jobs.values()) {
            if (existing.companyId.equals(companyId) && existing.month.equals(ym) && existing.finishedAt == null) {
                return view(existing);
            }
        }

        Job job = new Job(UUID.randomUUID(), companyId, ym);
        jobs.put(job.id, job);
        self().run(job);
        return view(job);
    }

    public Optional<PayrollJobResponse> find(UUID jobId) {
        UUID companyId = TenantContext.getCompanyId();
        Job job = jobs.get(jobId);
        if (job == null || !job.companyId.equals(companyId)) {
            return Optional.empty();
        }
        return Optional.of(view(job));
    }

    /**
     * The actual work, on the pool. The thread arrives with nothing bound: no request, so no
     * tenant on the connection it is about to borrow. Binding it here is what makes the run see
     * this company's rows — and, just as importantly, only this company's.
     */
    @Async(BackgroundWorkConfig.PAYROLL_EXECUTOR)
    public void run(Job job) {
        TenantContext.setCompanyId(job.companyId);
        MDC.put(TenantFilter.MDC_COMPANY, job.companyId.toString());
        long started = System.nanoTime();
        try {
            job.result = compensationService.payrollRun(job.month);
            log.info("Payroll run {} for {} finished: {} employees in {} ms", job.id, job.month,
                    job.result.employees(), (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException e) {
            job.error = e instanceof ApiException ? e.getMessage() : "The payroll run failed. Please try again.";
            log.error("Payroll run {} for {} failed after {} ms", job.id, job.month,
                    (System.nanoTime() - started) / 1_000_000, e);
        } finally {
            job.finishedAt = Instant.now();
            TenantContext.clear();
            MDC.remove(TenantFilter.MDC_COMPANY);
        }
    }

    private PayrollJobResponse view(Job job) {
        return new PayrollJobResponse(job.id.toString(), job.month, job.status(),
                job.startedAt.toString(), job.finishedAt == null ? null : job.finishedAt.toString(),
                job.result, job.error);
    }

    private void evictStale() {
        Instant cutoff = Instant.now().minus(KEEP_FINISHED);
        jobs.values().removeIf(j -> j.finishedAt != null && j.finishedAt.isBefore(cutoff));
    }

    private static String normalise(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now().toString();
        }
        try {
            return YearMonth.parse(month.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "month must be YYYY-MM");
        }
    }

    // @Async only applies through the Spring proxy; a call on `this` would run inline. The proxy
    // is looked up lazily to avoid a self-injection cycle at construction.
    private PayrollJobService self;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    void setSelf(PayrollJobService self) {
        this.self = self;
    }

    private PayrollJobService self() {
        return self;
    }
}
