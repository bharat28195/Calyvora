package com.calyvora.people;

/**
 * How a payslip component's amount is computed:
 * PERCENT_OF_GROSS — a percentage of monthly gross
 * PERCENT_OF_BASIC — a percentage of the basis earning (typically "Basic"), the usual base for PF etc.
 * FIXED           — a flat monthly amount
 * REMAINDER       — whatever is left of gross after the other earnings (keeps earnings summing to gross,
 *                   with no rounding drift). Only valid on an earning, at most once.
 */
public enum PayComponentCalc {
    PERCENT_OF_GROSS,
    PERCENT_OF_BASIC,
    FIXED,
    REMAINDER
}
