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
        String generatedBy,
        String createdAt
) {
    public static DocumentResponse of(GeneratedDocument d, String employeeName, String generatedBy) {
        return new DocumentResponse(d.getId().toString(), d.getTitle(), d.getKind().name(),
                d.getEmployeeId() == null ? null : d.getEmployeeId().toString(), employeeName,
                d.getTemplateId() == null ? null : d.getTemplateId().toString(),
                d.getBody(), generatedBy, d.getCreatedAt().toString());
    }
}
