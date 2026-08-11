package com.calyvora.recruit.dto;

/** The candidate after an offer was made, and the offer letter raised for them. */
public record OfferResponse(
        CandidateResponse candidate,
        String documentId,
        String documentTitle,
        /** Null when the letter was raised; otherwise why it wasn't. */
        String letterNote
) {
}
