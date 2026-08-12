package com.calyvora.trial.dto;

import com.calyvora.trial.TrialRequest;

/** One row of the vendor's trial queue. Console-only — this never goes back to the public caller. */
public record TrialRequestResponse(
        String id,
        String companyName,
        String contactName,
        String email,
        String phone,
        String teamSize,
        String note,
        String status,
        String source,
        String createdAt,
        String decidedAt,
        String companyId
) {
    public static TrialRequestResponse of(TrialRequest r) {
        return new TrialRequestResponse(
                r.getId().toString(), r.getCompanyName(), r.getContactName(), r.getEmail(),
                r.getPhone(), r.getTeamSize(), r.getNote(), r.getStatus().name(), r.getSource(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString(),
                r.getDecidedAt() == null ? null : r.getDecidedAt().toString(),
                r.getCompanyId() == null ? null : r.getCompanyId().toString());
    }
}
