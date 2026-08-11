package com.calyvora.document;

import com.calyvora.common.security.TenantContext;
import com.calyvora.company.CompanyRepository;
import com.calyvora.document.dto.LetterheadPayload;
import com.calyvora.document.dto.LetterheadResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The company letterpad (PD-20). Read on every letter preview, written from one screen.
 *
 * <p>Reading is get-or-create rather than "404 until configured": a company that has never opened
 * the screen still has a letterpad — its own name, the default type and colour — so the first letter
 * it ever issues is already headed properly. Nothing about the feature requires setup to work.
 */
@Service
public class LetterheadService {

    private final LetterheadRepository letterheadRepository;
    private final CompanyRepository companyRepository;

    public LetterheadService(LetterheadRepository letterheadRepository, CompanyRepository companyRepository) {
        this.letterheadRepository = letterheadRepository;
        this.companyRepository = companyRepository;
    }

    @Transactional
    public LetterheadResponse get() {
        return LetterheadResponse.of(getOrCreate(), companyName());
    }

    @Transactional
    public LetterheadResponse update(LetterheadPayload p) {
        Letterhead l = getOrCreate();
        // PATCH semantics throughout: null leaves the field alone, blank clears it. A full replace
        // here once wiped the details printed on payslips (PD-16) — the same mistake costs more on a
        // letterhead, where the loss is silent until someone reads a letter that went out headless.
        if (p.logoUrl() != null) l.setLogoUrl(blankToNull(p.logoUrl()));
        if (p.heading() != null) l.setHeading(blankToNull(p.heading()));
        if (p.addressLines() != null) l.setAddressLines(blankToNull(p.addressLines()));
        if (p.footerText() != null) l.setFooterText(blankToNull(p.footerText()));
        if (p.brandColor() != null) l.setBrandColor(p.brandColor());
        if (p.fontFamily() != null) l.setFontFamily(p.fontFamily());
        if (p.showDivider() != null) l.setShowDivider(p.showDivider());
        if (p.signatureName() != null) l.setSignatureName(blankToNull(p.signatureName()));
        if (p.signatureTitle() != null) l.setSignatureTitle(blankToNull(p.signatureTitle()));
        letterheadRepository.save(l);
        return LetterheadResponse.of(l, companyName());
    }

    /** The letterpad as a letter render needs it, including the resolved heading. */
    @Transactional
    public LetterheadResponse forRender() {
        return get();
    }

    private Letterhead getOrCreate() {
        UUID companyId = TenantContext.getCompanyId();
        return letterheadRepository.findById(companyId)
                .orElseGet(() -> letterheadRepository.save(new Letterhead(companyId)));
    }

    private String companyName() {
        return companyRepository.findById(TenantContext.getCompanyId())
                .map(com.calyvora.company.Company::getName)
                .orElse(null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
