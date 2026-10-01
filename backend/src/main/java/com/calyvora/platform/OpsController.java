package com.calyvora.platform;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Per-endpoint latency since the process started, for the platform owner.
 *
 * <p>Spring already times every request into Micrometer's {@code http.server.requests}; what was
 * missing was anywhere to read it. There is no Prometheus on the free tier and there will not be
 * one for a while, so this is the ops dashboard: which endpoints are slow, at the 95th percentile,
 * across every tenant. It answers "is the thing the customer complained about slow for everyone
 * or just for them" without reading a log.
 *
 * <p>Percentiles are computed in-process from the histogram switched on in {@code application.yml};
 * they reset on restart, which on a host that sleeps is often. Read them as "since the last wake".
 */
@RestController
@RequestMapping("/api/v1/platform/ops")
@PreAuthorize("hasRole('OWNER') and @platformAccess.granted()")
public class OpsController {

    private final MeterRegistry registry;

    public OpsController(MeterRegistry registry) {
        this.registry = registry;
    }

    /** One endpoint's numbers. Times in milliseconds. */
    public record EndpointStats(String method, String uri, long count, double meanMs, double p95Ms, double maxMs,
                                long errors) {
    }

    /**
     * The slowest endpoints by p95, busiest first among ties. {@code sort=count} gives the busiest
     * instead, which is the other question worth asking: what would speeding up help most people.
     */
    @GetMapping("/endpoints")
    public List<EndpointStats> endpoints(@RequestParam(defaultValue = "25") int top,
                                         @RequestParam(defaultValue = "p95") String sort) {
        // One timer per (method, uri, status, outcome...) combination; fold them to (method, uri).
        var byEndpoint = new java.util.LinkedHashMap<String, List<Timer>>();
        for (Timer timer : registry.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            if (uri == null || uri.startsWith("/actuator") || "UNKNOWN".equals(uri) || uri.startsWith("root")) {
                continue;
            }
            byEndpoint.computeIfAbsent(timer.getId().getTag("method") + " " + uri, k -> new java.util.ArrayList<>())
                    .add(timer);
        }

        List<EndpointStats> stats = new java.util.ArrayList<>();
        for (var entry : byEndpoint.entrySet()) {
            List<Timer> timers = entry.getValue();
            long count = 0;
            double totalMs = 0;
            double maxMs = 0;
            double p95 = 0;
            long errors = 0;
            for (Timer t : timers) {
                long n = t.count();
                count += n;
                totalMs += t.totalTime(TimeUnit.MILLISECONDS);
                maxMs = Math.max(maxMs, t.max(TimeUnit.MILLISECONDS));
                String status = t.getId().getTag("status");
                if (status != null && status.startsWith("5")) {
                    errors += n;
                }
                // Each timer has its own histogram; the endpoint's p95 is, conservatively, the
                // worst of them — usually the 200s, which is also the one with all the samples.
                for (ValueAtPercentile v : t.takeSnapshot().percentileValues()) {
                    if (Math.abs(v.percentile() - 0.95) < 0.001) {
                        p95 = Math.max(p95, v.value(TimeUnit.MILLISECONDS));
                    }
                }
            }
            if (count == 0) {
                continue;
            }
            String[] parts = entry.getKey().split(" ", 2);
            stats.add(new EndpointStats(parts[0], parts[1], count, round(totalMs / count), round(p95), round(maxMs),
                    errors));
        }

        Comparator<EndpointStats> order = "count".equals(sort)
                ? Comparator.comparingLong(EndpointStats::count).reversed()
                : Comparator.comparingDouble(EndpointStats::p95Ms).reversed()
                        .thenComparing(Comparator.comparingLong(EndpointStats::count).reversed());
        stats.sort(order);
        return stats.subList(0, Math.min(Math.max(top, 1), stats.size()));
    }

    private static double round(double ms) {
        return Math.round(ms * 10) / 10.0;
    }
}
