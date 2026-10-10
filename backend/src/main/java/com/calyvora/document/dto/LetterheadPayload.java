package com.calyvora.document.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A letterpad edit. Every field is optional and null means "leave it alone" — the screen saves the
 * whole form, but a caller sending one field should not blank the rest.
 *
 * <p>Normalising in the compact constructor means validation sees the cleaned value: a colour pasted
 * as {@code "7C5CFF "} is checked as {@code "#7c5cff"} and accepted, rather than rejected for a
 * missing hash the user cannot see.
 */
public record LetterheadPayload(
        @Size(max = 500, message = "Logo URL cannot be longer than 500 characters")
        String logoUrl,

        @Size(max = 160, message = "Heading cannot be longer than 160 characters")
        String heading,

        @Size(max = 400, message = "Address cannot be longer than 400 characters")
        String addressLines,

        @Size(max = 400, message = "Footer cannot be longer than 400 characters")
        String footerText,

        @Pattern(regexp = "^#([0-9a-f]{3}|[0-9a-f]{6}|[0-9a-f]{8})$",
                message = "Use a hex colour such as #7c5cff")
        String brandColor,

        @Pattern(regexp = "^(SERIF|SANS|SLAB)$", message = "Choose Serif, Sans or Slab")
        String fontFamily,

        Boolean showDivider,

        @Size(max = 120, message = "Signature name cannot be longer than 120 characters")
        String signatureName,

        @Size(max = 120, message = "Signature title cannot be longer than 120 characters")
        String signatureTitle,

        /** Print on the uploaded letterpad. Null leaves the choice alone, like every field here. */
        Boolean useBackground,

        // ---- V73: identity and standard terms ----
        @Size(max = 32) String cin,
        @Size(max = 20) String gstin,
        @Size(max = 160) String website,
        @Size(max = 160) String email,
        @Pattern(regexp = "^(LONG|SHORT|NUMERIC)$", message = "Choose a date style") String dateStyle,
        @jakarta.validation.constraints.Min(value = 0, message = "Probation cannot be negative")
        @jakarta.validation.constraints.Max(value = 730, message = "Probation is at most two years")
        Integer probationDays,
        @Size(max = 80) String noticeProbation,
        @Size(max = 80) String noticePeriod,
        @Size(max = 80) String workingDays,
        @Size(max = 80) String workingHours,
        @Size(max = 80) String payDay,
        @Size(max = 80) String jurisdiction,

        // ---- PD-69: page two onwards, and the writing area (millimetres on A4) ----
        @Pattern(regexp = "^(CONTINUATION|SAME)$", message = "Choose the continuation sheet or the full letterpad")
        String laterPages,
        @jakarta.validation.constraints.Min(5) @jakarta.validation.constraints.Max(180) Integer firstTopMm,
        @jakarta.validation.constraints.Min(5) @jakarta.validation.constraints.Max(180) Integer firstBottomMm,
        @jakarta.validation.constraints.Min(5) @jakarta.validation.constraints.Max(180) Integer laterTopMm,
        @jakarta.validation.constraints.Min(5) @jakarta.validation.constraints.Max(180) Integer laterBottomMm,
        @jakarta.validation.constraints.Min(8) @jakarta.validation.constraints.Max(50) Integer sideMm,
        /** Measure the letterpad again, discarding hand-set margins. */
        Boolean remeasure
) {
    public LetterheadPayload {
        logoUrl = trim(logoUrl);
        heading = trim(heading);
        addressLines = trim(addressLines);
        footerText = trim(footerText);
        signatureName = trim(signatureName);
        signatureTitle = trim(signatureTitle);
        cin = cin == null ? null : cin.trim().toUpperCase(java.util.Locale.ENGLISH);
        gstin = gstin == null ? null : gstin.trim().toUpperCase(java.util.Locale.ENGLISH);
        website = trim(website);
        email = trim(email);
        dateStyle = dateStyle == null ? null : dateStyle.trim().toUpperCase(java.util.Locale.ENGLISH);
        noticeProbation = trim(noticeProbation);
        noticePeriod = trim(noticePeriod);
        workingDays = trim(workingDays);
        workingHours = trim(workingHours);
        payDay = trim(payDay);
        jurisdiction = trim(jurisdiction);
        brandColor = normalizeColor(brandColor);
        fontFamily = fontFamily == null ? null : fontFamily.trim().toUpperCase(java.util.Locale.ENGLISH);
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }

    /** Accepts "7c5cff", "#7C5CFF" and " #7c5cff " alike. */
    private static String normalizeColor(String v) {
        if (v == null) {
            return null;
        }
        String s = v.trim().toLowerCase(java.util.Locale.ENGLISH);
        if (s.isEmpty()) {
            return null;
        }
        return s.startsWith("#") ? s : "#" + s;
    }
}
