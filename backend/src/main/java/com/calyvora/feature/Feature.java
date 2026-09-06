package com.calyvora.feature;

/**
 * Capabilities that can be switched on for one customer at a time.
 *
 * <p>Not a general-purpose flag system, and deliberately small: these are things a customer is
 * <em>sold</em> or opted into, not toggles for half-finished work. A flag that exists to hide an
 * unfinished feature should be deleted when the feature ships; these are expected to stay.
 *
 * <p>Everything here is <strong>off by default</strong>. A company that has never been touched
 * behaves exactly as it did before the flag existed, which is what makes rolling one out safe.
 */
public enum Feature {

    /**
     * Indian statutory payroll — Provident Fund today, ESI and professional tax to follow.
     *
     * <p>Off until a customer's numbers have been checked against their real payroll. This is the
     * first thing in the product that can print a wrong figure on somebody's payslip and have them
     * act on it, so it is trusted one company at a time.
     */
    STATUTORY_PAYROLL;

    public static Feature parse(String raw) {
        return Feature.valueOf(raw.trim().toUpperCase());
    }
}
