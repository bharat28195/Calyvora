package com.calyvora.document.dto;

import com.calyvora.document.GeneratedDocument;

/** A rendered, stored letter. */
public record DocumentResponse(
        String id,
        String title,
        String kind,
        String employeeId,
        String employeeName,
        String templateId,
        String body,
        boolean useLetterhead,
        String generatedBy,
        String createdAt,
        /** PD-71: whose signature prints on the letter, or null. */
        String signatoryId
) {
    public static DocumentResponse of(GeneratedDocument d, String employeeName, String generatedBy) {
        return new DocumentResponse(d.getId().toString(), d.getTitle(), d.getKind().name(),
                d.getEmployeeId() == null ? null : d.getEmployeeId().toString(),
                employeeName != null ? employeeName : d.getRecipientName(),
                d.getTemplateId() == null ? null : d.getTemplateId().toString(),
                d.getBody(), d.isUseLetterhead(), generatedBy, d.getCreatedAt().toString(),
                d.getSignatoryId() == null ? null : d.getSignatoryId().toString());
    }
}
