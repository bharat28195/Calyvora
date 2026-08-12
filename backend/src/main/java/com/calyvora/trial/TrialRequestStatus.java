package com.calyvora.trial;

/** Where a trial enquiry has got to. Only the platform owner moves it off {@link #NEW}. */
public enum TrialRequestStatus {
    NEW,
    APPROVED,
    DECLINED
}
