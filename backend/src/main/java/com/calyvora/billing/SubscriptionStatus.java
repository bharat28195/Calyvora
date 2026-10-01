package com.calyvora.billing;

/** Where a company's subscription stands. */
public enum SubscriptionStatus {
    /**
     * Created but never activated — the state an agency's new company starts in. The workspace exists
     * and its admin can sign in to see why it is locked, but nothing else works until the platform
     * owner activates it. Agencies create companies; only the vendor turns billing on (PD-18).
     */
    PENDING,
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED
}
