package com.calyvora.document;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import com.calyvora.email.EmailResult;
import com.calyvora.email.EmailSender;
import com.calyvora.email.EmailService;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An issued letter leaving Orbit (PD-64): as a PDF to download, and by email to the employee or a
 * new joinee — from "{company} via Orbit", with replies to the company's own address on the letterpad,
 * and every send recorded on the letter so HR can see it went.
 */
@Service
public class DocumentDeliveryService {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final GeneratedDocumentRepository documentRepository;
    private final DocumentEmailRepository emailRepository;
    private final LetterheadRepository letterheadRepository;
    private final CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public DocumentDeliveryService(GeneratedDocumentRepository documentRepository, DocumentEmailRepository emailRepository,
                                   LetterheadRepository letterheadRepository, CompanyRepository companyRepository,
                                   EmployeeRepository employeeRepository, UserRepository userRepository,
                                   EmailService emailService) {
        this.documentRepository = documentRepository;
        this.emailRepository = emailRepository;
        this.letterheadRepository = letterheadRepository;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    public record Pdf(String fileName, byte[] bytes) {
    }

    public record SendRequest(String to, String subject, String message) {
    }

    public record SentView(String id, String to, String subject, String sentAt, boolean delivered, String error) {
        static SentView of(DocumentEmail e) {
            return new SentView(e.getId().toString(), e.getSentTo(), e.getSubject(), e.getSentAt().toString(),
                    e.isDelivered(), e.getError());
        }
    }

    /** What the send form starts from: the employee's address, a subject and a short message. */
    public record SendDefaults(String to, String subject, String message, List<SentView> sent) {
    }

    @Transactional(readOnly = true)
    public Pdf pdf(UUID documentId) {
        GeneratedDocument doc = document(documentId);
        return new Pdf(fileName(doc), render(doc));
    }

    @Transactional(readOnly = true)
    public SendDefaults defaults(UUID documentId) {
        UUID companyId = TenantContext.getCompanyId();
        GeneratedDocument doc = document(documentId);
        String to = null;
        if (doc.getEmployeeId() != null) {
            to = employeeRepository.findByIdAndCompanyId(doc.getEmployeeId(), companyId)
                    .flatMap(e -> userRepository.findByIdAndCompanyId(e.getUserId(), companyId))
                    .map(u -> u.getEmail()).orElse(null);
        }
        String company = companyName(companyId);
        String who = doc.getTitle().contains(" — ") ? doc.getTitle().substring(doc.getTitle().indexOf(" — ") + 3) : null;
        String message = "Dear " + (who == null ? "colleague" : who.split("\\s+")[0]) + ",\n\n"
                + "Please find your " + kindName(doc).toLowerCase(Locale.ENGLISH) + " from " + company + " attached.\n\n"
                + "If you have any questions, just reply to this email.\n\nRegards,\n" + company;
        List<SentView> sent = new ArrayList<>();
        for (DocumentEmail e : emailRepository.findByDocumentIdOrderBySentAtDesc(doc.getId())) sent.add(SentView.of(e));
        return new SendDefaults(to, kindName(doc) + " — " + company, message, sent);
    }

    @Transactional
    public SendDefaults send(UUID documentId, SendRequest req, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        GeneratedDocument doc = document(documentId);
        String to = req.to() == null ? "" : req.to().trim();
        if (!EMAIL.matcher(to).matches() || to.length() > 320) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Enter a valid email address to send the letter to.");
        }
        String subject = req.subject() == null || req.subject().isBlank() ? doc.getTitle() : req.subject().trim();
        if (subject.length() > 300) subject = subject.substring(0, 300);
        String message = req.message() == null ? "" : req.message().trim();
        if (message.length() > 5000) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Keep the message under 5,000 characters.");
        }
        Letterhead lh = letterheadRepository.findById(companyId).orElse(null);
        String replyTo = lh == null ? null : lh.getEmail();
        EmailResult result = emailService.sendDocument(to, companyName(companyId), replyTo, subject, message,
                new EmailSender.Attachment(fileName(doc), "application/pdf", render(doc)));
        emailRepository.save(new DocumentEmail(companyId, doc.getId(), to, subject, principal.userId(),
                result.delivered(), result.delivered() ? null : result.error()));
        if (!result.delivered()) {
            throw new ApiException(ErrorCode.CONFLICT, "The letter could not be sent: " + result.error());
        }
        return defaults(documentId);
    }

    // ---- helpers -------------------------------------------------------------------------------------

    private byte[] render(GeneratedDocument doc) {
        UUID companyId = TenantContext.getCompanyId();
        LetterPdf.Stationery paper = null;
        if (doc.isUseLetterhead()) {
            Letterhead l = letterheadRepository.findById(companyId).orElse(null);
            String company = companyName(companyId);
            if (l == null) {
                paper = new LetterPdf.Stationery(company, null, null, null, true, null, null);
            } else {
                List<String> footer = new ArrayList<>();
                String ids = join("  ·  ", l.getCin() == null ? null : "CIN: " + l.getCin(),
                        l.getGstin() == null ? null : "GSTIN: " + l.getGstin());
                String web = join("  ·  ", l.getWebsite() == null ? null : "Website: " + l.getWebsite(),
                        l.getEmail() == null ? null : "Email: " + l.getEmail());
                if (ids != null) footer.add(ids);
                if (web != null) footer.add(web);
                if (l.getFooterText() != null) footer.add(l.getFooterText());
                boolean printed = l.isUseBackground() && l.getBackgroundImage() != null;
                paper = new LetterPdf.Stationery(l.getHeading() == null ? company : l.getHeading(), l.getAddressLines(),
                        String.join("\n", footer), l.getBrandColor(), !"SANS".equals(l.getFontFamily()), l.getLogoUrl(),
                        printed ? l.getBackgroundImage() : null);
            }
        }
        try {
            return LetterPdf.render(doc.getBody(), paper);
        } catch (IOException | RuntimeException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "The letter could not be turned into a PDF.");
        }
    }

    private GeneratedDocument document(UUID id) {
        return documentRepository.findByIdAndCompanyId(id, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("Document not found"));
    }

    private String companyName(UUID companyId) {
        return companyRepository.findById(companyId).map(com.calyvora.company.Company::getName).orElse("Your employer");
    }

    private static String kindName(GeneratedDocument doc) {
        String t = doc.getTitle();
        return t.contains(" — ") ? t.substring(0, t.indexOf(" — ")) : t;
    }

    /** "Appointment letter — Priya Nair.pdf", safe for every mail client and file system. */
    private static String fileName(GeneratedDocument doc) {
        String base = doc.getTitle().replaceAll("[\\\\/:*?\"<>|\\r\\n]", " ").replaceAll("\\s+", " ").trim();
        if (base.length() > 120) base = base.substring(0, 120).trim();
        return (base.isEmpty() ? "Letter" : base) + ".pdf";
    }

    private static String join(String sep, String a, String b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a + sep + b;
    }
}
