package com.calyvora.people;

/** How an employee's day is recorded (feedback C.4). */
public enum AttendanceStatus {
    PRESENT,
    WORK_FROM_HOME,
    HALF_DAY,
    ABSENT,
    ON_LEAVE,
    HOLIDAY,
    WEEK_OFF;

    /** Days that count as worked when summarizing a month (half day counts as half — see the service). */
    public boolean isWorking() {
        return this == PRESENT || this == WORK_FROM_HOME || this == HALF_DAY;
    }

    /** Days nobody was expected in, so they don't drag the attendance rate down. */
    public boolean isNonWorkingDay() {
        return this == HOLIDAY || this == WEEK_OFF;
    }
}
