package com.calyvora.document.dto;

import com.calyvora.document.DocumentTemplate;
import com.calyvora.document.MergeFields;

import java.util.List;

/** A template plus the merge fields its body actually uses. */
public record TemplateResponse(
        String id,
        String name,
        String kind,
        String description,
        String body,
        boolean builtIn,
        boolean useLetterhead,
        List<String> placeholders,
        String updatedAt
) {
    public static TemplateResponse of(DocumentTemplate t) {
        return new TemplateResponse(t.getId().toString(), t.getName(), t.getKind().name(), t.getDescription(),
                t.getBody(), t.isBuiltIn(), t.isUseLetterhead(),
                MergeFields.placeholdersIn(t.getBody()), t.getUpdatedAt().toString());
    }
}
