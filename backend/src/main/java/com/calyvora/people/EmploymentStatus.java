package com.calyvora.people;

public enum EmploymentStatus {
    ONBOARDING,
    ACTIVE,
    /** Exit started, last working day not yet reached — still an employee, still on payroll (PD-20). */
    NOTICE,
    TERMINATED
}
