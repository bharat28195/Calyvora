package com.calyvora.recruit;

/** The hiring pipeline a candidate moves through. Order matters — it's the board's column order. */
public enum CandidateStage {
    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    HIRED,
    REJECTED
}
