package com.calyvora.tax;

/** Where a declared line stands with its proof. */
public enum ProofStatus {
    /** Nothing uploaded yet. */
    NONE,
    /** Uploaded, waiting for HR. */
    SUBMITTED,
    /** HR accepted all of it. */
    ACCEPTED,
    /** HR accepted part of it — the accepted amount says how much. */
    PARTIAL,
    /** HR accepted none of it. */
    REJECTED
}
