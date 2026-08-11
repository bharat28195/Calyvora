package com.calyvora.recruit.dto;

/**
 * What came of hiring someone: where the candidate now sits, the letter that was raised, and the
 * joining link.
 *
 * <p>{@code joinLink} is returned to the caller as well as emailed, for the same reason invitations
 * always have been — onboarding a colleague must not depend on mail being deliverable.
 */
public record HireResponse(
        CandidateResponse candidate,
        String documentId,
        String documentTitle,
        String invitationId,
        String joinLink,
        /** Null when nothing went wrong; otherwise why the letter could not be raised. */
        String letterNote
) {
}
