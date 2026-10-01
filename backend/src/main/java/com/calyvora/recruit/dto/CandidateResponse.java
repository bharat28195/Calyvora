package com.calyvora.recruit.dto;

import com.calyvora.recruit.Candidate;

/** A candidate in the pipeline. */
public record CandidateResponse(
        String id,
        String jobId,
        String name,
        String email,
        String phone,
        String resumeUrl,
        String source,
        String stage,
        Integer rating,
        String notes,
        String createdAt
) {
    public static CandidateResponse of(Candidate c) {
        return new CandidateResponse(
                c.getId().toString(), c.getJobId().toString(), c.getName(), c.getEmail(), c.getPhone(),
                c.getResumeUrl(), c.getSource(), c.getStage().name(), c.getRating(), c.getNotes(),
                c.getCreatedAt().toString());
    }
}
