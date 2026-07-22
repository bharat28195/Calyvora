package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Daily attendance (feedback C.4): marking, self check-in/out, leave fallback, month summary, RBAC. */
class AttendanceIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    void admin_marks_a_day_and_it_shows_on_the_team_sheet() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = anyEmployeeId(owner);
        String day = lastWeekday().toString();

        mark(owner, employeeId, day, "WORK_FROM_HOME", "09:30", "18:00", "Remote sprint day");

        JsonNode sheet = getJson("/api/v1/people/attendance/day?date=" + day, owner);
        assertThat(sheet.get("present").asInt()).isEqualTo(1);
        assertThat(sheet.get("unmarked").asInt()).isZero();
        JsonNode entry = sheet.get("entries").get(0);
        assertThat(entry.get("status").asText()).isEqualTo("WORK_FROM_HOME");
        assertThat(entry.get("checkIn").asText()).startsWith("09:30");
        assertThat(entry.get("derived").asBoolean()).isFalse();
    }

    @Test
    void marking_the_same_day_twice_corrects_it_rather_than_duplicating() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = anyEmployeeId(owner);
        String day = lastWeekday().toString();

        mark(owner, employeeId, day, "ABSENT", null, null, null);
        mark(owner, employeeId, day, "PRESENT", "10:00", null, "Was actually in");

        JsonNode sheet = getJson("/api/v1/people/attendance/day?date=" + day, owner);
        assertThat(sheet.get("entries")).hasSize(1);            // one person, one row
        assertThat(sheet.get("entries").get(0).get("status").asText()).isEqualTo("PRESENT");
        assertThat(sheet.get("absent").asInt()).isZero();
    }

    @Test
    void an_unmarked_day_falls_back_to_approved_leave() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        LocalDate day = lastWeekday();

        MvcResult req = mockMvc.perform(post("/api/v1/people/leave").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "VACATION", "startDate", day.toString(),
                                "endDate", day.toString(), "reason", "Family holiday"))))
                .andExpect(status().isCreated()).andReturn();
        String leaveId = objectMapper.readTree(req.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/people/leave/" + leaveId + "/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        JsonNode entry = getJson("/api/v1/people/attendance/day?date=" + day, owner).get("entries").get(0);
        assertThat(entry.get("status").asText()).isEqualTo("ON_LEAVE");
        assertThat(entry.get("derived").asBoolean()).isTrue();   // nobody marked it; we inferred it
        assertThat(entry.get("note").asText()).contains("Family holiday");
    }

    @Test
    void a_marked_day_wins_over_the_derived_leave_value() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = anyEmployeeId(owner);
        LocalDate day = lastWeekday();

        MvcResult req = mockMvc.perform(post("/api/v1/people/leave").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "SICK", "startDate", day.toString(), "endDate", day.toString()))))
                .andExpect(status().isCreated()).andReturn();
        String leaveId = objectMapper.readTree(req.getResponse().getContentAsString()).get("id").asText();
        mockMvc.perform(post("/api/v1/people/leave/" + leaveId + "/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        mark(owner, employeeId, day.toString(), "PRESENT", "09:00", null, "Came in anyway");

        JsonNode entry = getJson("/api/v1/people/attendance/day?date=" + day, owner).get("entries").get(0);
        assertThat(entry.get("status").asText()).isEqualTo("PRESENT");
        assertThat(entry.get("derived").asBoolean()).isFalse();
    }

    @Test
    void self_check_in_then_check_out() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        JsonNode in = postJson("/api/v1/people/attendance/me/check-in", owner);
        assertThat(in.get("status").asText()).isEqualTo("PRESENT");
        String firstCheckIn = in.get("checkIn").asText();

        // idempotent — clocking in twice doesn't move the time
        assertThat(postJson("/api/v1/people/attendance/me/check-in", owner).get("checkIn").asText())
                .isEqualTo(firstCheckIn);

        assertThat(postJson("/api/v1/people/attendance/me/check-out", owner).get("checkOut").isNull()).isFalse();
        assertThat(getJson("/api/v1/people/attendance/me/today", owner).get("status").asText()).isEqualTo("PRESENT");
    }

    @Test
    void month_summary_counts_worked_days_and_ignores_week_offs() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = anyEmployeeId(owner);
        LocalDate day = lastWeekday();

        mark(owner, employeeId, day.toString(), "HALF_DAY", "09:00", "13:00", null);

        JsonNode month = getJson("/api/v1/people/attendance/employees/" + employeeId
                + "?month=" + day.getYear() + "-" + String.format("%02d", day.getMonthValue()), owner);
        assertThat(month.get("days").size()).isEqualTo(day.lengthOfMonth());
        assertThat(month.get("counts").get("HALF_DAY").asInt()).isEqualTo(1);
        assertThat(month.get("counts").get("WEEK_OFF").asInt()).isPositive();   // weekends resolve on their own
        assertThat(month.get("workedDays").asDouble()).isEqualTo(0.5);
        assertThat(month.get("attendanceRate").isNull()).isFalse();
    }

    @Test
    void members_can_clock_themselves_but_not_see_or_mark_the_team() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session member = login("priya.nair@northwind.demo", "demopass123");   // MEMBER

        // own attendance: allowed
        mockMvc.perform(get("/api/v1/people/attendance/me").header("Authorization", bearer(member)))
                .andExpect(status().isOk());

        // the team sheet and marking someone else: denied
        mockMvc.perform(get("/api/v1/people/attendance/day").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/people/attendance/mark").header("Authorization", bearer(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", anyEmployeeId(member), "status", "PRESENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void future_dates_are_rejected() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        mockMvc.perform(post("/api/v1/people/attendance/mark").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", anyEmployeeId(owner), "status", "PRESENT",
                                "date", LocalDate.now().plusDays(1).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void the_team_overview_reports_how_much_of_present_is_unmarked() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");

        JsonNode overview = getJson("/api/v1/dashboard/team", owner);
        assertThat(overview.get("headcount").asInt()).isEqualTo(6);
        assertThat(overview.has("unmarkedToday")).isTrue();
        assertThat(overview.get("presentToday").asInt() + overview.get("onLeaveToday").asInt())
                .isLessThanOrEqualTo(overview.get("headcount").asInt());
    }

    // ---- helpers ----

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private void mark(Session s, String employeeId, String date, String status,
                      String checkIn, String checkOut, String note) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("employeeId", employeeId);
        body.put("date", date);
        body.put("status", status);
        if (checkIn != null) body.put("checkIn", checkIn);
        if (checkOut != null) body.put("checkOut", checkOut);
        if (note != null) body.put("note", note);
        mockMvc.perform(post("/api/v1/people/attendance/mark").header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    private JsonNode postJson(String path, Session s) throws Exception {
        MvcResult res = mockMvc.perform(post(path).header("Authorization", bearer(s)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String anyEmployeeId(Session s) throws Exception {
        return getJson("/api/v1/people/employees", s).get(0).get("id").asText();
    }

    /** A weekday in the recent past — weekends resolve to WEEK_OFF, which would confuse these assertions. */
    private static LocalDate lastWeekday() {
        LocalDate d = LocalDate.now().minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }
}
