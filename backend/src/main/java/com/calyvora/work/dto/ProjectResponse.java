package com.calyvora.work.dto;

import com.calyvora.work.Project;

import java.math.BigDecimal;

public record ProjectResponse(
        String id,
        String name,
        String key,
        String description,
        String status,
        String leadUserId,
        String leadName,
        long taskCount,
        long openTaskCount,
        String createdAt,
        // --- commercials (V44, D2). Not money anyone earns — just what was agreed. ---
        String clientId,
        String clientName,
        boolean billable,
        BigDecimal contractValue
) {
    public static ProjectResponse of(Project p, String leadName, long taskCount, long openTaskCount,
                                     String clientName) {
        return new ProjectResponse(
                p.getId().toString(), p.getName(), p.getKey(), p.getDescription(),
                p.getStatus().name(),
                p.getLeadUserId() == null ? null : p.getLeadUserId().toString(),
                leadName, taskCount, openTaskCount, p.getCreatedAt().toString(),
                p.getClientId() == null ? null : p.getClientId().toString(),
                clientName, p.isBillable(), p.getContractValue());
    }
}
