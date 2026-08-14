package com.calyvora.trial;

import com.calyvora.common.config.AppProperties;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.email.EmailService;
import com.calyvora.email.TrialEnquiry;
import com.calyvora.platform.PlatformService;
import com.calyvora.platform.dto.CompanySummaryResponse;
import com.calyvora.platform.dto.CreateCompanyRequest;
import com.calyvora.trial.dto.ApproveTrialRequest;
import com.calyvora.trial.dto.TrialRequestForm;
import com.calyvora.trial.dto.TrialRequestResponse;
import com.calyvora.company.Company;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * "Start free trial" as an enquiry rather than a signup (PD-21).
 *
 * <p>Two halves. The public one takes a form from an anonymous visitor, stores it and tells the
 * vendor — it creates nothing that can be signed into. The console one lets the platform owner turn
 * an enquiry into a real customer, which goes through {@link PlatformService#provision} exactly like
 * a company the owner adds by hand: one provisioning path, one set of rules about seats and billing.
 */
@Service
public class TrialRequestService {

    private static final Logger log = LoggerFactory.getLogger(TrialRequestService.class);

    private final TrialRequestRepository repository;
    private final PlatformService platformService;
    private final EmailService emailService;
    private final AppProperties props;
    private final String platformOwnerEmail;

    public TrialRequestService(TrialRequestRepository repository,
                               PlatformService platformService,
                               EmailService emailService,
                               AppProperties props,
                               @Value("${calyvora.platform.owner-email:bharat28195@calyvora.in}")
                               String platformOwnerEmail) {
        this.repository = repository;
        this.platformService = platformService;
        this.emailService = emailService;
        this.props = props;
        this.platformOwnerEmail = platformOwnerEmail;
    }

    // ---- public surface ---------------------------------------------------

    /**
     * Record an enquiry and tell the vendor about it.
     *
     * <p>Submitting twice is not an error. A visitor who doesn't see an instant confirmation clicks
     * again, and answering "you already asked" to that reads as a rejection of a perfectly reasonable
     * act — so a repeat while the first is still open quietly returns the request already on file.
     * The vendor's queue stays one row per person, which is what makes it usable.
     *
     * @return whether the acknowledgement actually reached the requester's mailbox, so the page can
     *         say "check your email" only when something was sent.
     */
    @Transactional
    public Result submit(TrialRequestForm form) {
        TrialRequest existing = repository.findByEmailAndStatus(form.email(), TrialRequestStatus.NEW)
                .orElse(null);
        if (existing != null) {
            // Deliberately no second notification: the vendor already has this one in the queue.
            return new Result(TrialRequestResponse.of(existing), false, true);
        }

        TrialRequest request = repository.save(new TrialRequest(UUID.randomUUID(),
                form.companyName(), form.contactName(), form.email(),
                form.phone(), form.teamSize(), form.note(), form.source()));

        notifyVendor(request);
        boolean acknowledged = emailService
                .sendTrialRequestAcknowledgement(request.getEmail(), request.getContactName())
                .delivered();
        return new Result(TrialRequestResponse.of(request), acknowledged, false);
    }

    /**
     * @param request     the stored enquiry
     * @param acknowledged whether we managed to email the requester back
     * @param duplicate   true when this was a repeat of a request already waiting
     */
    public record Result(TrialRequestResponse request, boolean acknowledged, boolean duplicate) {
    }

    // ---- vendor console ---------------------------------------------------

    @Transactional(readOnly = true)
    public List<TrialRequestResponse> all() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(TrialRequestResponse::of).toList();
    }

    /**
     * Grant the trial: provision the company and its first ADMIN on the terms the owner just set, then
     * tell the customer the workspace is ready.
     *
     * <p>The enquiry is only marked approved once provisioning succeeds — if the email is already
     * registered, say, the row stays {@code NEW} so the owner can fix it and try again rather than
     * being left with an "approved" request that produced no customer.
     */
    @Transactional
    public CompanySummaryResponse approve(UUID id, ApproveTrialRequest terms) {
        TrialRequest request = require(id);
        requireOpen(request);

        Company company = platformService.provision(new CreateCompanyRequest(
                request.getCompanyName(),
                firstName(request.getContactName()),
                lastName(request.getContactName()),
                request.getEmail(),
                terms.password(),
                terms.seats(),
                terms.months(),
                null), null, true);

        request.decide(TrialRequestStatus.APPROVED, company.getId());
        emailService.sendTrialApprovedEmail(request.getEmail(), company.getName(),
                props.frontendBaseUrl() + "/login");
        return platformService.summarize(company);
    }

    @Transactional
    public TrialRequestResponse decline(UUID id) {
        TrialRequest request = require(id);
        requireOpen(request);
        // No email. Turning someone down is a conversation the vendor has, not an automated brush-off.
        request.decide(TrialRequestStatus.DECLINED, null);
        return TrialRequestResponse.of(request);
    }

    // ---- helpers ----------------------------------------------------------

    private void notifyVendor(TrialRequest request) {
        String to = notifyAddress();
        var result = emailService.sendTrialRequestNotification(to, new TrialEnquiry(
                request.getCompanyName(), request.getContactName(), request.getEmail(),
                request.getPhone(), request.getTeamSize(), request.getNote(), request.getSource(),
                props.frontendBaseUrl() + "/platform"));
        if (!result.delivered()) {
            // The request is safe in the queue either way, so this must not fail the visitor's
            // submission — but an enquiry nobody hears about is the whole failure mode this feature
            // exists to prevent, so it is logged at a level that shows up.
            log.error("Trial request from {} <{}> was saved but the notification to {} was not "
                            + "delivered. Check it in the platform console.",
                    request.getCompanyName(), request.getEmail(), to);
        }
    }

    private String notifyAddress() {
        String configured = props.trial() == null ? null : props.trial().notifyEmail();
        return configured == null || configured.isBlank() ? platformOwnerEmail : configured.trim();
    }

    private TrialRequest require(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Trial request not found"));
    }

    private static void requireOpen(TrialRequest request) {
        if (request.getStatus() != TrialRequestStatus.NEW) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "This request was already " + request.getStatus().name().toLowerCase() + ".");
        }
    }

    /**
     * People type their name in one box; the model wants two. Everything before the first space is the
     * first name, the rest is the surname, and a single-word name repeats — because a blank surname
     * fails validation, and refusing "Madonna" at the last step of an approval is absurd.
     */
    private static String firstName(String contactName) {
        int space = contactName.trim().indexOf(' ');
        return space < 0 ? contactName.trim() : contactName.trim().substring(0, space);
    }

    private static String lastName(String contactName) {
        int space = contactName.trim().indexOf(' ');
        return space < 0 ? contactName.trim() : contactName.trim().substring(space + 1).trim();
    }
}
