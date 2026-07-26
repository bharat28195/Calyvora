package com.calyvora.billing;

/** Where a company's subscription stands. */
public enum SubscriptionStatus {
    TRIALING,
    ACTIVE,
    PAST_DUE,
    CANCELLED
}
