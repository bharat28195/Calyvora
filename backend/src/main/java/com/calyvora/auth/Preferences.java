package com.calyvora.auth;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;

import java.util.List;

/**
 * The languages and formats a person may choose, and what "no choice" falls back to.
 *
 * <p>English is the default everywhere: for a person who has not chosen, and for a company whose
 * stored locale is something the app has no words for yet.
 */
public final class Preferences {

    private Preferences() {
    }

    public static final String DEFAULT_LANGUAGE = "en";

    /** Languages the app has text for. Matches the dictionaries in the frontend's lib/i18n. */
    public static final List<String> LANGUAGES = List.of("en", "hi", "es", "fr", "de");

    public static final List<String> DATE_FORMATS = List.of("DMY", "MDY", "YMD");

    public static final List<String> TIME_FORMATS = List.of("H12", "H24");

    /**
     * The language someone sees: their own choice, else their company's, else English. A company
     * locale such as "en-GB" counts as its language ("en").
     */
    public static String effectiveLanguage(String own, String companyLocale) {
        String mine = supported(own);
        if (mine != null) {
            return mine;
        }
        String company = supported(companyLocale);
        return company == null ? DEFAULT_LANGUAGE : company;
    }

    /** Blank means "use the default" and is stored as null; anything unknown is a 400. */
    public static String validLanguage(String raw) {
        return oneOf(raw, LANGUAGES, "language");
    }

    public static String validDateFormat(String raw) {
        return oneOf(raw, DATE_FORMATS, "date format");
    }

    public static String validTimeFormat(String raw) {
        return oneOf(raw, TIME_FORMATS, "time format");
    }

    private static String supported(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String base = raw.trim().toLowerCase(java.util.Locale.ROOT).split("[-_]")[0];
        return LANGUAGES.contains(base) ? base : null;
    }

    private static String oneOf(String raw, List<String> allowed, String what) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!allowed.contains(value)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "'" + value + "' is not a " + what + " Orbit offers. Choose one of " + allowed + ".");
        }
        return value;
    }
}
