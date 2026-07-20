package com.calyvora.common.util;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Company slug generation. Lower-cases, strips accents, and collapses non-alphanumerics to single
 * hyphens. Uniqueness (the {@code companies.slug} constraint) is handled by the caller, which
 * appends a short suffix on collision.
 */
public final class Slugs {

    private Slugs() {}

    public static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "company";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.length() > 120) {
            slug = slug.substring(0, 120).replaceAll("-+$", "");
        }
        return slug.isBlank() ? "company" : slug;
    }
}
