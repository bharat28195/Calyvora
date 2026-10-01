package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Holiday calendar: CRUD, RBAC, upcoming, and the effect on attendance. */
class HolidayIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    void admins_manage_the_calendar_and_everyone_can_read_it() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");
        Session member = login("priya.nair@northwind.demo", "demopass123");

        // readable by a member — you need to know when the office is shut
        assertThat(getJson("/api/v1/people/holidays", member).size()).isPositive();

        // but not editable by them
        mockMvc.perform(post("/api/v1/people/holidays").header("Authorization", bearer(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Nap Day", "date", LocalDate.now().plusDays(3).toString()))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/people/holidays").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Company Offsite", "date", LocalDate.now().plusDays(3).toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void upcoming_returns_future_holidays_nearest_first_with_days_away() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        create(owner, "Far off", LocalDate.now().plusDays(40), false);
        create(owner, "Soon", LocalDate.now().plusDays(4), false);
        create(owner, "Already gone", LocalDate.now().minusDays(5), false);

        JsonNode upcoming = getJson("/api/v1/people/holidays/upcoming", owner);
        assertThat(upcoming.size()).isEqualTo(2);                       // the past one is excluded
        assertThat(upcoming.get(0).get("name").asText()).isEqualTo("Soon");
        assertThat(upcoming.get(0).get("daysAway").asInt()).isEqualTo(4);
        assertThat(upcoming.get(0).get("weekday").asText()).isNotBlank();
    }

    @Test
    void a_holiday_fills_the_attendance_day_for_everyone() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        LocalDate day = lastWeekday();
        create(owner, "Founders' Day", day, false);

        JsonNode entry = getJson("/api/v1/people/attendance/day?date=" + day, owner).get("entries").get(0);
        assertThat(entry.get("status").asText()).isEqualTo("HOLIDAY");
        assertThat(entry.get("derived").asBoolean()).isTrue();
        assertThat(entry.get("note").asText()).isEqualTo("Founders' Day");
    }

    @Test
    void an_optional_holiday_does_not_close_the_day() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        LocalDate day = lastWeekday();
        create(owner, "Volunteering Day", day, true);

        JsonNode entry = getJson("/api/v1/people/attendance/day?date=" + day, owner).get("entries").get(0);
        assertThat(entry.get("status").isNull()).isTrue();   // still just unmarked
    }

    @Test
    void a_marked_day_still_wins_over_a_holiday() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        LocalDate day = lastWeekday();
        create(owner, "Founders' Day", day, false);
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/people/attendance/mark").header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("employeeId", employeeId, "date", day.toString(), "status", "PRESENT"))))
                .andExpect(status().isOk());

        JsonNode entry = getJson("/api/v1/people/attendance/day?date=" + day, owner).get("entries").get(0);
        assertThat(entry.get("status").asText()).isEqualTo("PRESENT");
    }

    @Test
    void the_starter_calendar_seeds_once() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        mockMvc.perform(post("/api/v1/people/holidays/defaults").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        int first = getJson("/api/v1/people/holidays", owner).size();
        assertThat(first).isPositive();

        mockMvc.perform(post("/api/v1/people/holidays/defaults").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/people/holidays", owner).size()).isEqualTo(first);
    }

    @Test
    void holidays_are_tenant_isolated() throws Exception {
        Session a = onboardOwner("Company A", "a@a.com", PW);
        create(a, "A-only holiday", LocalDate.now().plusDays(2), false);

        Session b = onboardOwner("Company B", "b@b.com", PW);
        assertThat(getJson("/api/v1/people/holidays", b).size()).isZero();
    }

    // ---- helpers ----

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private void create(Session s, String name, LocalDate date, boolean optional) throws Exception {
        mockMvc.perform(post("/api/v1/people/holidays").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "date", date.toString(), "optional", optional))))
                .andExpect(status().isCreated());
    }

    /** Weekends resolve to WEEK_OFF, which would mask what these tests are checking. */
    private static LocalDate lastWeekday() {
        LocalDate d = LocalDate.now().minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }
}
