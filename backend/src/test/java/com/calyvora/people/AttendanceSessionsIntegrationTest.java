package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V70: several check-ins a day, gross vs effective hours, the 9-hour day, half day when short,
 * absent after shift start + grace, and managers correcting many days at once with a reason.
 */
class AttendanceSessionsIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void a_break_is_a_second_session_and_the_day_keeps_first_in_and_last_out() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        JsonNode in = post("/api/v1/people/attendance/me/check-in", owner);
        assertThat(in.get("openSince").isNull()).isFalse();
        assertThat(in.get("requiredMinutes").asInt()).isEqualTo(540);     // nine hours by default

        post("/api/v1/people/attendance/me/check-out", owner);
        JsonNode back = post("/api/v1/people/attendance/me/check-in", owner);
        assertThat(back.get("sessions")).hasSize(2);
        assertThat(back.get("checkIn").asText()).isEqualTo(in.get("checkIn").asText());
        assertThat(back.get("checkOut").isNull()).isTrue();                // back in: no last-out yet

        // A second check-in while one is running does nothing.
        assertThat(post("/api/v1/people/attendance/me/check-in", owner).get("sessions")).hasSize(2);

        JsonNode out = post("/api/v1/people/attendance/me/check-out", owner);
        assertThat(out.get("openSince").isNull()).isTrue();
        assertThat(out.get("effectiveMinutes").asInt()).isLessThanOrEqualTo(out.get("grossMinutes").asInt());
    }

    @Test
    void a_finished_day_short_of_the_hours_is_a_half_day_until_a_manager_corrects_it() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = anyEmployeeId(owner);
        LocalDate day = lastWeekday();
        UUID companyId = companyOf(owner);
        rulesFrom(companyId, day.minusDays(30));

        // Clocked themselves 09:00–13:00 and 14:00–16:00: six hours against nine.
        asTenant(companyId, "insert into attendance_records (id, company_id, employee_id, on_date, status, check_in, check_out) "
                + "values (?, ?, ?, ?, 'PRESENT', '09:00', '16:00')", UUID.randomUUID(), companyId,
                UUID.fromString(employeeId), day);
        punch(companyId, employeeId, day, "09:00", "13:00");
        punch(companyId, employeeId, day, "14:00", "16:00");

        JsonNode entry = dayEntry(owner, day);
        assertThat(entry.get("status").asText()).isEqualTo("HALF_DAY");
        assertThat(entry.get("effectiveMinutes").asInt()).isEqualTo(360);
        assertThat(entry.get("grossMinutes").asInt()).isEqualTo(420);
        assertThat(entry.get("note").asText()).isEqualTo("Short by 3h 00m");

        correct(owner, List.of(employeeId), List.of(day.toString()), "PRESENT", "Client site, confirmed")
                .andExpect(status().isOk());
        assertThat(dayEntry(owner, day).get("status").asText()).isEqualTo("PRESENT");
    }

    @Test
    void no_check_in_by_start_plus_grace_is_absent_but_only_from_when_the_rules_began() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String employeeId = anyEmployeeId(owner);
        LocalDate day = lastWeekday();

        UUID companyId = companyOf(owner);
        rulesFrom(companyId, day.plusDays(1));
        assertThat(dayEntry(owner, day).get("status").isNull()).isTrue();   // before the rules: untouched

        rulesFrom(companyId, day);
        JsonNode entry = dayEntry(owner, day);
        assertThat(entry.get("status").asText()).isEqualTo("ABSENT");
        assertThat(entry.get("derived").asBoolean()).isTrue();
    }

    @Test
    void a_correction_needs_a_reason_and_a_lead_reaches_only_their_own_team() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/dev/seed-demo"))
                .andExpect(status().isOk());
        Session priya = login("priya.nair@northwind.demo", "demopass123");   // leads Dev, nobody else
        Session ava = login("ava.chen@northwind.demo", "demopass123");
        String dev = employeeIdByName(ava, "Dev");
        String tom = employeeIdByName(ava, "Tom");
        String day = lastWeekday().toString();

        correct(priya, List.of(dev), List.of(day), "PRESENT", " ").andExpect(status().isBadRequest());
        correct(priya, List.of(tom), List.of(day), "PRESENT", "Was in").andExpect(status().isForbidden());
        correct(priya, List.of(dev), List.of(day), "WORK_FROM_HOME", "Worked from home").andExpect(status().isOk());
        // Ava (admin) may correct anyone, and many at once.
        correct(ava, List.of(dev, tom), List.of(day), "PRESENT", "Offsite").andExpect(status().isOk());
    }

    @Test
    void a_regularization_without_a_reason_is_refused() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/attendance/regularizations")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("date", lastWeekday().toString(), "checkIn", "09:30", "reason", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void present_today_counts_only_people_who_checked_in() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        boolean weekend = isWeekendForCompany();
        assertThat(getJson("/api/v1/dashboard/team", owner).get("presentToday").asInt()).isZero();
        post("/api/v1/people/attendance/me/check-in", owner);
        JsonNode overview = getJson("/api/v1/dashboard/team", owner);
        assertThat(overview.get("presentToday").asInt()).isEqualTo(1);
        assertThat(overview.get("absentToday").asInt()).isZero();
        if (!weekend) {
            assertThat(overview.get("unmarkedToday").asInt()).isZero();
        }
    }

    // ---- helpers ----

    private JsonNode post(String path, Session s) throws Exception {
        MvcResult res = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                        .header("Authorization", "Bearer " + s.accessToken()))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions correct(Session s, List<String> employeeIds,
                                                                       List<String> dates, String status,
                                                                       String reason) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/people/attendance/correct")
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("employeeIds", employeeIds, "dates", dates, "status", status, "reason", reason))));
    }

    private JsonNode dayEntry(Session s, LocalDate day) throws Exception {
        return getJson("/api/v1/people/attendance/day?date=" + day, s).get("entries").get(0);
    }

    private String anyEmployeeId(Session s) throws Exception {
        return getJson("/api/v1/people/employees", s).get(0).get("id").asText();
    }

    private String employeeIdByName(Session s, String firstName) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", s)) {
            if (e.get("firstName").asText().equals(firstName)) return e.get("id").asText();
        }
        throw new AssertionError("No employee named " + firstName);
    }

    /** The tenant from the access token: tables are row-level secured, so raw SQL must name it. */
    private UUID companyOf(Session s) throws Exception {
        String payload = s.accessToken().split("[.]")[1];
        JsonNode claims = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(payload));
        return UUID.fromString(claims.get("companyId").asText());
    }

    /** One statement on one connection with the tenant bound, as the app itself does per request. */
    private int asTenant(UUID companyId, String sql, Object... args) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) c -> {
            try (var set = c.prepareStatement("select set_config('calyvora.company_id', ?, false)")) {
                set.setString(1, companyId.toString());
                set.execute();
            }
            try (var st = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) st.setObject(i + 1, args[i]);
                return st.executeUpdate();
            } finally {
                try (var reset = c.prepareStatement("select set_config('calyvora.company_id', '', false)")) {
                    reset.execute();
                }
            }
        });
    }

    private void rulesFrom(UUID companyId, LocalDate from) {
        int rows = asTenant(companyId, "update company_settings set attendance_rules_from = ? where company_id = ?",
                from, companyId);
        if (rows == 0) {
            asTenant(companyId, "insert into company_settings (company_id, timezone, locale, currency, updated_at, "
                    + "attendance_rules_from) values (?, 'Asia/Kolkata', 'en', 'INR', now(), ?)", companyId, from);
        }
    }

    private void punch(UUID companyId, String employeeId, LocalDate day, String in, String out) {
        asTenant(companyId, "insert into attendance_punches (id, company_id, employee_id, on_date, check_in, check_out) "
                + "values (?, ?, ?, ?, ?::time, ?::time)", UUID.randomUUID(), companyId, UUID.fromString(employeeId),
                day, in, out);
    }

    private static boolean isWeekendForCompany() {
        DayOfWeek d = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    /** A weekday in the recent past, so weekends don't resolve the day to WEEK_OFF. */
    private static LocalDate lastWeekday() {
        LocalDate d = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).minusDays(1);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.minusDays(1);
        }
        return d;
    }
}
