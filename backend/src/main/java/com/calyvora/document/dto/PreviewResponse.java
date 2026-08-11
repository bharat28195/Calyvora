package com.calyvora.document.dto;

import java.util.List;
import java.util.Map;

/**
 * A dry-run render: the resolved body plus which fields came back empty, so the issuer can fix the
 * profile (or override the value) *before* a letter goes out with a dash in it.
 */
public record PreviewResponse(
        String title,
        String body,
        boolean useLetterhead,
        Map<String, String> values,
        List<String> missing
) {}
