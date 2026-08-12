package com.calyvora.trial.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

/**
 * What the public "request a free trial" form sends. Anonymous callers post this, so it asks for the
 * least that lets a human decide — who you are, where you work, how to reach you — and nothing that
 * could become credentials. Notably <em>no password</em>: there is no account to set one on yet.
 */
public record TrialRequestForm(
        @NotBlank @Size(max = 200) String companyName,
        @NotBlank @Size(max = 200) String contactName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 40) String phone,
        @Size(max = 40) String teamSize,
        @Size(max = 2000) String note,
        @Size(max = 80) String source
) {
    /**
     * Trim and lower-case before validation, not after. A pasted address with a trailing space is a
     * typo, not a rejection — and the email has to be normalised here rather than in the service,
     * because the "one open request per address" index compares the stored value.
     */
    public TrialRequestForm {
        companyName = trim(companyName);
        contactName = trim(contactName);
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = blankToNull(phone);
        teamSize = blankToNull(teamSize);
        note = blankToNull(note);
        source = blankToNull(source);
    }

    private static String trim(String v) {
        return v == null ? null : v.trim();
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }
}
