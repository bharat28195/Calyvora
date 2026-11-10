package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Loss of pay is a share of the whole month, whenever the payslip is worked out (D2).
 *
 * <p>Working days with nothing recorded yet — the rest of the month — used to be left out of the
 * month's total, so one absence on the 2nd cost a month's salary and a run on the 28th still took too
 * much. They are paid days and count.
 */
class PayslipLossOfPayTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    @DisplayName("one absence costs one working day's pay, not the days elapsed so far")
    void one_absence_is_one_day_of_the_whole_month() throws Exception {
        Session owner = onboardOwner("Lopco", "owner@lopco.test", PW);
        // Nobody becomes absent by the clock; the one absence below is the only one.
        attendanceRulesOff(owner);

        JsonNode me = getJson("/api/v1/people/employees", owner).get(0);
        String employeeId = me.get("id").asText();
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", 1_200_000, "effectiveDate", "2026-01-01", "reason", "initial"))))
                .andExpect(status().isOk());

        YearMonth month = YearMonth.now(ZoneId.of("Asia/Kolkata"));
        LocalDate first = month.atDay(1);
        if (me.hasNonNull("startDate")) {
            LocalDate start = LocalDate.parse(me.get("startDate").asText());
            if (start.isAfter(first)) first = start;
        }
        Set<LocalDate> holidays = new HashSet<>();
        for (JsonNode h : getJson("/api/v1/people/holidays", owner)) {
            if (!h.path("optional").asBoolean(false)) holidays.add(LocalDate.parse(h.get("date").asText()));
        }
        int workingDays = 0;
        LocalDate absentOn = null;
        for (LocalDate d = first; !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (weekend || holidays.contains(d)) continue;
            workingDays++;
            if (absentOn == null) absentOn = d;
        }
        assertThat(absentOn).as("this month has a working day to be absent on").isNotNull();

        mockMvc.perform(post("/api/v1/people/attendance/mark")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId, "date", absentOn.toString(), "status", "ABSENT"))))
                .andExpect(status().isOk());

        JsonNode slip = getJson("/api/v1/people/employees/" + employeeId + "/payslip?month=" + month, owner);
        assertThat(slip.get("lopDays").asDouble()).isEqualTo(1.0);
        assertThat(slip.get("workingDays").asInt()).as("every working day of the month, not only those recorded")
                .isEqualTo(workingDays);

        // What was earned is the month less exactly one working day. The month's gross is read from the
        // payslip: someone who joined this month has it prorated already.
        BigDecimal gross = new BigDecimal(slip.get("gross").asText());
        BigDecimal oneDay = gross.divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);
        assertThat(slip.get("payableDays").asDouble()).isEqualTo(workingDays - 1.0);
        assertThat(new BigDecimal(slip.get("net").asText())).isEqualByComparingTo(gross.subtract(oneDay));
    }
}
