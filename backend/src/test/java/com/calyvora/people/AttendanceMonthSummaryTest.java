package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The month behind a calendar grid: counts per day, scoped by the reporting tree (PD-32). */
class AttendanceMonthSummaryTest extends IntegrationTestBase {

    private static final String PW = "demopass123";
    private static final String MONTH = YearMonth.now().toString();

    private Session demo(String email) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login(email, PW);
    }

    @Test
    @DisplayName("an admin gets one row per day of the month, for the whole company")
    void a_row_per_day_for_everyone() throws Exception {
        Session ava = demo("ava.chen@northwind.demo");
        JsonNode summary = getJson("/api/v1/people/attendance/month-summary?month=" + MONTH, ava);

        assertThat(summary.get("month").asText()).isEqualTo(MONTH);
        assertThat(summary.get("headcount").asInt()).isEqualTo(7);
        assertThat(summary.get("days")).hasSize(YearMonth.parse(MONTH).lengthOfMonth());

        // Every person is accounted for on every day — that is what makes the bars proportional
        // rather than merely present.
        for (JsonNode day : summary.get("days")) {
            long counted = day.get("present").asLong() + day.get("onLeave").asLong()
                    + day.get("absent").asLong() + day.get("unmarked").asLong() + day.get("weekOff").asLong();
            assertThat(counted).as("every employee counted on %s", day.get("date").asText()).isEqualTo(7);
        }
    }

    @Test
    @DisplayName("a lead sees their own downline's month, not the company's")
    void a_lead_sees_only_their_downline() throws Exception {
        Session tom = demo("tom.becker@northwind.demo");   // manages Sara, and only Sara
        JsonNode summary = getJson("/api/v1/people/attendance/month-summary?month=" + MONTH, tom);

        assertThat(summary.get("headcount").asInt()).isEqualTo(1);
        for (JsonNode day : summary.get("days")) {
            long counted = day.get("present").asLong() + day.get("onLeave").asLong()
                    + day.get("absent").asLong() + day.get("unmarked").asLong() + day.get("weekOff").asLong();
            assertThat(counted).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("someone with nobody under them is refused")
    void no_reports_no_grid() throws Exception {
        Session dev = demo("dev.sharma@northwind.demo");
        mockMvc.perform(get("/api/v1/people/attendance/month-summary")
                        .header("Authorization", "Bearer " + dev.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a company holiday is named on the day it falls")
    void holidays_are_named() throws Exception {
        Session ava = demo("ava.chen@northwind.demo");
        String date = YearMonth.parse(MONTH).atDay(15).toString();
        mockMvc.perform(post("/api/v1/people/holidays")
                        .header("Authorization", "Bearer " + ava.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("name", "Founders' Day", "date", date))))
                .andExpect(status().isCreated());

        JsonNode summary = getJson("/api/v1/people/attendance/month-summary?month=" + MONTH, ava);
        JsonNode day = null;
        for (JsonNode d : summary.get("days")) {
            if (date.equals(d.get("date").asText())) {
                day = d;
            }
        }
        assertThat(day).isNotNull();
        assertThat(day.get("holiday").asBoolean()).isTrue();
        assertThat(day.get("holidayName").asText()).isEqualTo("Founders' Day");
    }
}
