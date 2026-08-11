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
        String signatureTitle
) {
    public LetterheadPayload {
        logoUrl = trim(logoUrl);
        heading = trim(heading);
        addressLines = trim(addressLines);
        footerText = trim(footerText);
        signatureName = trim(signatureName);
        signatureTitle = trim(signatureTitle);
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
