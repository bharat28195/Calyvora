package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the attendance percentage is a percentage <em>of</em>.
 *
 * <p>It used to be worked days over days that happened to have a row, so somebody who checked in
 * three times in a month saw "3 / 3 days" and "100%". The number was not measuring attendance; it
 * was measuring how many days had records, which is always all of them.
 *
 * <p>The denominator is every working day of the month that has already passed — weekends and
 * holidays excluded, since nobody was expected in — whether or not anything was recorded on it.
 */
class AttendanceRateTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    private Session demo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login("ava.chen@northwind.demo", PW);
    }

    @Test
    @DisplayName("an unrecorded working day counts against the rate rather than vanishing from it")
    void unrecorded_days_are_still_expected() throws Exception {
        Session ava = demo();
        YearMonth month = YearMonth.now();
        JsonNode m = getJson("/api/v1/people/attendance/me?month=" + month, ava);

        long expected = m.get("expectedDays").asLong();
        long notRecorded = m.get("notRecorded").asLong();
        double worked = m.get("workedDays").asDouble();

        // The counted statuses, from the same payload, so this is checking the response against
        // itself rather than against a number copied out of the implementation.
        long recordedWorkingDays = 0;
        for (JsonNode day : m.get("days")) {
            String status = day.get("status").isNull() ? null : day.get("status").asText();
            LocalDate date = LocalDate.parse(day.get("date").asText());
            if (status == null || "HOLIDAY".equals(status) || "WEEK_OFF".equals(status)) {
                continue;
            }
            if (!date.isAfter(LocalDate.now())) {
                recordedWorkingDays++;
            }
        }

        assertThat(expected)
                .as("expected is the recorded working days plus the ones nobody recorded")
                .isEqualTo(recordedWorkingDays + notRecorded);
        assertThat(worked).isLessThanOrEqualTo(expected);
    }

    @Test
    @DisplayName("the denominator is the month's elapsed working days, counted from the first")
    void the_denominator_is_elapsed_working_days() throws Exception {
        Session ava = demo();
        YearMonth month = YearMonth.now();
        JsonNode m = getJson("/api/v1/people/attendance/me?month=" + month, ava);

        // Weekdays from the 1st up to yesterday — today is excluded while it has no record, because
        // the day is not over and counting it as missed would show a dip every morning.
        LocalDate today = LocalDate.now();
        long weekdaysBeforeToday = 0;
        for (LocalDate d = month.atDay(1); d.isBefore(today) && !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                weekdaysBeforeToday++;
            }
        }

        long expected = m.get("expectedDays").asLong();
        // Company holidays fall inside that span and are not expected days, so the count is a
        // ceiling rather than an equality. Today may add one more if it carries a record.
        assertThat(expected).isLessThanOrEqualTo(weekdaysBeforeToday + 1);

        // The real point: on any day past the first working day of the month, the denominator must
        // be more than the handful of days that happen to have rows on them.
        if (weekdaysBeforeToday > 3) {
            assertThat(expected)
                    .as("the rate must be measured against the month so far, not against the records")
                    .isGreaterThan(1);
        }
    }

    @Test
    @DisplayName("a perfect rate means every elapsed working day was worked, not that one was")
    void a_hundred_percent_has_to_be_earned() throws Exception {
        Session ava = demo();
        YearMonth month = YearMonth.now();
        JsonNode m = getJson("/api/v1/people/attendance/me?month=" + month, ava);

        if (m.get("attendanceRate").isNull()) {
            return;   // nothing has happened yet this month; nothing to assert
        }
        double rate = m.get("attendanceRate").asDouble();
        long notRecorded = m.get("notRecorded").asLong();

        // The defect in one line: the seeded demo marks only a few days, so before this change the
        // rate was 100%. With unrecorded days in the denominator it cannot be, and if it ever is
        // again it must be because there is genuinely nothing unrecorded.
        if (notRecorded > 0) {
            assertThat(rate).as("%d unrecorded working days cannot leave a perfect rate", notRecorded)
                    .isLessThan(100.0);
        }
    }
}
