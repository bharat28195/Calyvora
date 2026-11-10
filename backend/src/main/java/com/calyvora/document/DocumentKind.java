package com.calyvora.document;

/**
 * The kind of letter a template produces (feedback D2/D3; the full library in PD-63). One per stage
 * of somebody's time at a company — hiring, probation, growth, verification, discipline and exit — so
 * the kind can drive where a letter is offered (a relieving letter from the exit screen, an offer
 * from the candidate) without reading template names.
 *
 * <p>Stored by name, so new kinds can be added anywhere in the list.
 */
public enum DocumentKind {
    // hiring
    OFFER_LETTER,
    APPOINTMENT_LETTER,
    INTERNSHIP_OFFER,
    JOINING_LETTER,
    // probation
    CONFIRMATION_LETTER,
    PROBATION_EXTENSION,
    // growth
    INCREMENT_LETTER,
    PROMOTION_LETTER,
    TRANSFER_LETTER,
    BONUS_LETTER,
    APPRECIATION_LETTER,
    // verification
    SALARY_CERTIFICATE,
    EMPLOYMENT_VERIFICATION,
    NOC,
    // discipline
    WARNING_LETTER,
    SHOW_CAUSE_NOTICE,
    PIP_LETTER,
    TERMINATION_LETTER,
    // exit
    RESIGNATION_ACCEPTANCE,
    RELIEVING_LETTER,
    EXPERIENCE_LETTER,
    NO_DUES_CERTIFICATE,
    FNF_STATEMENT,
    INTERNSHIP_CERTIFICATE,
    CUSTOM,
}
