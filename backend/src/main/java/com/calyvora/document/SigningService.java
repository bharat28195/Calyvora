package com.calyvora.document;

import com.calyvora.common.config.AppProperties;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantBinder;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import com.calyvora.email.EmailResult;
import com.calyvora.email.EmailService;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.notification.NotificationService;
import com.calyvora.notification.NotificationType;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Letters accepted with a signature (PD-72).
 *
 * <p>HR sends an issued letter to the person it is for. An employee signs inside Orbit (My letters);
 * a candidate, who has no login yet, signs through a link emailed to them that only works for that
 * one letter and expires. Either way they read the letter as it will print — the same pages as the
 * PDF — then type their name, draw their signature and accept, or decline with a reason.
 *
 * <p>What makes it a record rather than a checkbox: the letter's text is fingerprinted when sent and
 * checked again at signing, and the signer's signature, name, time, IP address and browser are kept
 * and printed on an audit page at the end of the signed PDF. This is an electronic signature in the
 * everyday sense used for offer and appointment letters; it is not an Aadhaar eSign.
 */
@Service
public class SigningService {

    static final Duration DEFAULT_VALIDITY = Duration.ofDays(14);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final long MAX_SIGNATURE_BYTES = 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SignatureRequestRepository requests;
    private final SignatureLinkRepository links;
    private final GeneratedDocumentRepository documents;
    private final EmployeeRepository employees;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final LetterheadRepository letterheads;
    private final EmailService email;
    private final NotificationService notifications;
    private final AppProperties props;
    private final TenantBinder tenantBinder;
    private final DocumentDeliveryService delivery;

    public SigningService(SignatureRequestRepository requests, SignatureLinkRepository links,
                          GeneratedDocumentRepository documents, EmployeeRepository employees, UserRepository users,
                          CompanyRepository companies, LetterheadRepository letterheads, EmailService email,
                          NotificationService notifications, AppProperties props, TenantBinder tenantBinder,
                          DocumentDeliveryService delivery) {
        this.requests = requests;
        this.links = links;
        this.documents = documents;
        this.employees = employees;
        this.users = users;
        this.companies = companies;
        this.letterheads = letterheads;
        this.email = email;
        this.notifications = notifications;
        this.props = props;
        this.tenantBinder = tenantBinder;
        this.delivery = delivery;
    }

    // ---- views ----

    public record RequestView(String id, String documentId, String documentTitle, String signerName,
                              String signerEmail, boolean employee, String status, String sentAt, String viewedAt,
                              String decidedAt, String expiresAt, String declineReason, String typedName,
                              int reminders, boolean open) {
    }

    /** What the signer sees: the letter as its printed pages, and what they are being asked. */
    public record SignerView(String requestId, String companyName, String documentTitle, String signerName,
                             String message, String status, String expiresAt, String decidedAt,
                             List<String> pages) {
    }

    public record SendRequest(String email, String message, Integer validDays) {
    }

    public record AcceptRequest(String typedName, String signature, Boolean agree) {
    }

    public record DeclineRequest(String reason) {
    }

    // ---- HR side ----

    /** Sends (or re-sends, replacing any open request) a letter for signature. */
    @Transactional
    public RequestView send(UUID documentId, SendRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        GeneratedDocument doc = document(documentId);
        Instant now = Instant.now();
        for (SignatureRequest r : requests.findByDocumentIdOrderBySentAtDesc(documentId)) {
            if (r.getStatus() == SignatureRequest.Status.SIGNED) {
                throw new ApiException(ErrorCode.CONFLICT, "This letter has already been signed.");
            }
            if (r.isOpen(now)) r.withdraw(now);
        }

        String name;
        String to;
        UUID employeeId = doc.getEmployeeId();
        if (employeeId != null) {
            Employee e = employees.findByIdAndCompanyId(employeeId, companyId)
                    .orElseThrow(() -> new NotFoundException("Employee not found"));
            User u = users.findByIdAndCompanyId(e.getUserId(), companyId)
                    .orElseThrow(() -> new NotFoundException("Employee has no account"));
            name = u.getFirstName() + " " + u.getLastName();
            to = req != null && notBlank(req.email()) ? req.email().trim() : u.getEmail();
        } else {
            name = doc.getRecipientName() == null ? "there" : doc.getRecipientName();
            to = req == null ? null : req.email() == null ? null : req.email().trim();
            if (to == null || to.isEmpty()) {
                throw invalid("Enter the email address to send the signing link to.");
            }
        }
        if (!EMAIL.matcher(to).matches() || to.length() > 320) throw invalid("Enter a valid email address.");
        int days = req == null || req.validDays() == null ? (int) DEFAULT_VALIDITY.toDays() : req.validDays();
        if (days < 1 || days > 60) throw invalid("A signing request can be open for 1 to 60 days.");
        String message = req == null || req.message() == null ? null : req.message().trim();
        if (message != null && message.length() > 2000) throw invalid("Keep the message under 2,000 characters.");

        SignatureRequest r = new SignatureRequest(companyId, documentId, employeeId, name, to, hash(doc.getBody()),
                message == null || message.isEmpty() ? null : message, now.plus(Duration.ofDays(days)), principal.userId());
        requests.save(r);
        String link = linkFor(r);
        deliver(r, doc, link);
        if (employeeId != null) {
            employees.findByIdAndCompanyId(employeeId, companyId).ifPresent(e ->
                    notifications.send(companyId, e.getUserId(), principal.userId(), NotificationType.LETTER_TO_SIGN,
                            "Please sign: " + doc.getTitle(), "Read it and accept with your signature.",
                            "/me/letters/" + r.getId(), "DOCUMENT", doc.getId()));
        }
        return view(r, doc);
    }

    @Transactional(readOnly = true)
    public List<RequestView> forDocument(UUID documentId) {
        GeneratedDocument doc = document(documentId);
        return requests.findByDocumentIdOrderBySentAtDesc(documentId).stream().map(r -> view(r, doc)).toList();
    }

    @Transactional
    public RequestView withdraw(UUID requestId) {
        SignatureRequest r = request(requestId);
        if (!r.isOpen(Instant.now())) throw new ApiException(ErrorCode.CONFLICT, "This request is no longer open.");
        r.withdraw(Instant.now());
        links.deleteAll(links.findByRequestId(r.getId()));
        return view(r, document(r.getDocumentId()));
    }

    /** Emails the signer again and, if the request was about to lapse, gives it another week. */
    @Transactional
    public RequestView remind(UUID requestId) {
        SignatureRequest r = request(requestId);
        Instant now = Instant.now();
        if (!r.isOpen(now)) throw new ApiException(ErrorCode.CONFLICT, "This request is no longer open.");
        r.reminded(now, now.plus(Duration.ofDays(7)));
        GeneratedDocument doc = document(r.getDocumentId());
        deliver(r, doc, linkFor(r));
        return view(r, doc);
    }

    // ---- employee side ----

    @Transactional(readOnly = true)
    public List<RequestView> mine(AuthPrincipal principal) {
        Employee me = employees.findByUserId(principal.userId()).orElse(null);
        if (me == null) return List.of();
        return requests.findByEmployeeIdOrderBySentAtDesc(me.getId()).stream()
                .filter(r -> r.getStatus() != SignatureRequest.Status.WITHDRAWN)
                .map(r -> view(r, document(r.getDocumentId()))).toList();
    }

    @Transactional
    public SignerView openMine(UUID requestId, AuthPrincipal principal) {
        SignatureRequest r = myRequest(requestId, principal);
        r.viewed(Instant.now());
        return signerView(r);
    }

    @Transactional
    public SignerView acceptMine(UUID requestId, AcceptRequest a, String ip, String agent, AuthPrincipal principal) {
        SignatureRequest r = myRequest(requestId, principal);
        accept(r, a, ip, agent);
        return signerView(r);
    }

    @Transactional
    public SignerView declineMine(UUID requestId, DeclineRequest d, String ip, String agent, AuthPrincipal principal) {
        SignatureRequest r = myRequest(requestId, principal);
        decline(r, d, ip, agent);
        return signerView(r);
    }

    /** The signed PDF of one of my letters, once I have signed it. */
    @Transactional(readOnly = true)
    public DocumentDeliveryService.Pdf myPdf(UUID requestId, AuthPrincipal principal) {
        SignatureRequest r = myRequest(requestId, principal);
        return delivery.pdf(r.getDocumentId());
    }

    // ---- public link (candidates) ----

    @Transactional
    public SignerView openByToken(String token) {
        return onLink(token, r -> {
            r.viewed(Instant.now());
            return signerView(r);
        });
    }

    @Transactional
    public SignerView acceptByToken(String token, AcceptRequest a, String ip, String agent) {
        return onLink(token, r -> {
            accept(r, a, ip, agent);
            return signerView(r);
        });
    }

    @Transactional
    public SignerView declineByToken(String token, DeclineRequest d, String ip, String agent) {
        return onLink(token, r -> {
            decline(r, d, ip, agent);
            return signerView(r);
        });
    }

    @Transactional(readOnly = true)
    public DocumentDeliveryService.Pdf pdfByToken(String token) {
        return onLink(token, r -> {
            if (r.getStatus() != SignatureRequest.Status.SIGNED) {
                throw new ApiException(ErrorCode.CONFLICT, "The signed copy is available once the letter is signed.");
            }
            return delivery.pdf(r.getDocumentId());
        });
    }

    private <T> T onLink(String token, java.util.function.Function<SignatureRequest, T> work) {
        if (token == null || token.length() < 20 || token.length() > 100) throw linkGone();
        SignatureLink link = links.findById(sha256(token)).orElseThrow(SigningService::linkGone);
        // A signed letter stays readable through its link for a while, to download the copy.
        if (Instant.now().isAfter(link.getExpiresAt().plus(Duration.ofDays(30)))) throw linkGone();
        return tenantBinder.callAs(link.getCompanyId(), () -> {
            SignatureRequest r = requests.findByIdAndCompanyId(link.getRequestId(), link.getCompanyId())
                    .orElseThrow(SigningService::linkGone);
            return work.apply(r);
        });
    }

    // ---- the decision ----

    private void accept(SignatureRequest r, AcceptRequest a, String ip, String agent) {
        requireOpen(r);
        if (a == null || !Boolean.TRUE.equals(a.agree())) throw invalid("Tick the box to confirm you accept this letter.");
        String typed = a.typedName() == null ? "" : a.typedName().trim();
        if (typed.length() < 2 || typed.length() > 200) throw invalid("Type your full name.");
        byte[] png = signaturePng(a.signature());
        GeneratedDocument doc = documents.findByIdAndCompanyId(r.getDocumentId(), r.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Letter not found"));
        if (!hash(doc.getBody()).equals(r.getDocumentHash())) {
            throw new ApiException(ErrorCode.CONFLICT, "This letter has changed since it was sent. Ask HR to send it again.");
        }
        r.sign(typed, png, clip(ip, 64), clip(agent, 400), Instant.now());
        tellSender(r, doc, NotificationType.LETTER_SIGNED, r.getSignerName() + " signed " + doc.getTitle(), "Accepted and signed.");
    }

    private void decline(SignatureRequest r, DeclineRequest d, String ip, String agent) {
        requireOpen(r);
        String reason = d == null || d.reason() == null ? "" : d.reason().trim();
        if (reason.isEmpty()) throw invalid("Tell them why, so they can follow up.");
        if (reason.length() > 500) reason = reason.substring(0, 500);
        r.decline(reason, clip(ip, 64), clip(agent, 400), Instant.now());
        GeneratedDocument doc = documents.findByIdAndCompanyId(r.getDocumentId(), r.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Letter not found"));
        tellSender(r, doc, NotificationType.LETTER_DECLINED, r.getSignerName() + " declined " + doc.getTitle(), reason);
    }

    private void tellSender(SignatureRequest r, GeneratedDocument doc, NotificationType type, String title, String body) {
        if (r.getSentBy() == null) return;
        notifications.send(r.getCompanyId(), r.getSentBy(), null, type, title, body,
                "/documents/" + doc.getId(), "DOCUMENT", doc.getId());
    }

    private static void requireOpen(SignatureRequest r) {
        Instant now = Instant.now();
        if (r.getStatus() == SignatureRequest.Status.SIGNED) throw new ApiException(ErrorCode.CONFLICT, "You have already signed this letter.");
        if (r.getStatus() == SignatureRequest.Status.DECLINED) throw new ApiException(ErrorCode.CONFLICT, "You have already declined this letter.");
        if (r.getStatus() == SignatureRequest.Status.WITHDRAWN) throw new ApiException(ErrorCode.CONFLICT, "This letter was withdrawn. Ask HR for the current one.");
        if (!r.isOpen(now)) throw new ApiException(ErrorCode.CONFLICT, "This signing request has expired. Ask HR to send it again.");
    }

    /** The drawn signature: a PNG data URI from the signing pad, cleaned like an uploaded one. */
    private static byte[] signaturePng(String dataUri) {
        if (dataUri == null || !dataUri.startsWith("data:image/png;base64,")) throw invalid("Draw or type your signature.");
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
        } catch (IllegalArgumentException e) {
            throw invalid("Draw or type your signature.");
        }
        if (raw.length > MAX_SIGNATURE_BYTES) throw invalid("That signature image is too large.");
        try {
            return SignatureImages.clean(raw);
        } catch (java.io.IOException e) {
            throw invalid("Draw or type your signature.");
        }
    }

    // ---- helpers ----

    private SignerView signerView(SignatureRequest r) {
        GeneratedDocument doc = documents.findByIdAndCompanyId(r.getDocumentId(), r.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Letter not found"));
        String company = companies.findById(r.getCompanyId()).map(com.calyvora.company.Company::getName).orElse("");
        String status = r.isOpen(Instant.now()) || r.getStatus() != SignatureRequest.Status.SENT && r.getStatus() != SignatureRequest.Status.VIEWED
                ? r.getStatus().name() : "EXPIRED";
        return new SignerView(r.getId().toString(), company, doc.getTitle(), r.getSignerName(), r.getMessage(), status,
                r.getExpiresAt().toString(), r.getDecidedAt() == null ? null : r.getDecidedAt().toString(),
                delivery.pageImages(doc.getId()));
    }

    private RequestView view(SignatureRequest r, GeneratedDocument doc) {
        Instant now = Instant.now();
        boolean open = r.isOpen(now);
        String status = !open && (r.getStatus() == SignatureRequest.Status.SENT || r.getStatus() == SignatureRequest.Status.VIEWED)
                ? "EXPIRED" : r.getStatus().name();
        return new RequestView(r.getId().toString(), doc.getId().toString(), doc.getTitle(), r.getSignerName(),
                r.getSignerEmail(), r.getEmployeeId() != null, status, r.getSentAt().toString(),
                r.getViewedAt() == null ? null : r.getViewedAt().toString(),
                r.getDecidedAt() == null ? null : r.getDecidedAt().toString(), r.getExpiresAt().toString(),
                r.getDeclineReason(), r.getTypedName(), r.getReminders(), open);
    }

    /** Employees sign in Orbit; anyone else gets a fresh single-letter link (older links stop working). */
    private String linkFor(SignatureRequest r) {
        String base = props.frontendBaseUrl();
        if (r.getEmployeeId() != null) return base + "/me/letters/" + r.getId();
        links.deleteAll(links.findByRequestId(r.getId()));
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        links.save(new SignatureLink(sha256(token), r.getCompanyId(), r.getId(), r.getExpiresAt()));
        return base + "/sign/" + token;
    }

    private void deliver(SignatureRequest r, GeneratedDocument doc, String link) {
        String company = companies.findById(r.getCompanyId()).map(com.calyvora.company.Company::getName).orElse("Your employer");
        String replyTo = letterheads.findById(r.getCompanyId()).map(Letterhead::getEmail).orElse(null);
        String until = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                .format(r.getExpiresAt().atZone(ZoneId.of("Asia/Kolkata")));
        EmailResult sent = email.sendSignatureRequest(r.getSignerEmail(), company, replyTo, r.getSignerName(),
                doc.getTitle(), link, r.getMessage(), until);
        if (!sent.delivered()) {
            org.slf4j.LoggerFactory.getLogger(SigningService.class)
                    .warn("Signing request {} not emailed: {}", r.getId(), sent.error());
        }
    }

    private SignatureRequest myRequest(UUID requestId, AuthPrincipal principal) {
        Employee me = employees.findByUserId(principal.userId()).orElseThrow(() -> new NotFoundException("Letter not found"));
        SignatureRequest r = request(requestId);
        if (!me.getId().equals(r.getEmployeeId())) throw new NotFoundException("Letter not found");
        if (r.getStatus() == SignatureRequest.Status.WITHDRAWN) throw new NotFoundException("Letter not found");
        return r;
    }

    private SignatureRequest request(UUID id) {
        return requests.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Signing request not found"));
    }

    private GeneratedDocument document(UUID id) {
        return documents.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    static String hash(String body) {
        return sha256(body == null ? "" : body);
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String clip(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    private static NotFoundException linkGone() {
        return new NotFoundException("This signing link is not valid any more. Ask the company to send it again.");
    }
}
