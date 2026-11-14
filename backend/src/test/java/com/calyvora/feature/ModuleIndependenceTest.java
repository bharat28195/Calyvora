package com.calyvora.feature;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A small company buys part of Orbit and the rest must neither show nor get in the way (2026-11-14).
 *
 * <p>Each test switches one module off and checks the modules it used to leak into: pay screens that
 * sat outside the payroll gate, working-day settings that lived behind Shifts, and absence rules that
 * docked pay for a company that never tracked attendance here.
 */
class ModuleIndependenceTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private Session platformOwner() throws Exception {
        platformOwner.ensurePlatformOwner();
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    private void switchOff(String companyName, String feature) throws Exception {
        Session platform = platformOwner();
        String companyId = null;
        for (JsonNode c : getJson("/api/v1/platform/companies", platform)) {
            if (companyName.equals(c.get("name").asText())) companyId = c.get("companyId").asText();
        }
        assertThat(companyId).as("company " + companyName).isNotNull();
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("feature", feature, "enabled", false))))
                .andExpect(status().isOk());
    }

    private JsonNode workDay(Session owner) throws Exception {
        String body = mockMvc.perform(get("/api/v1/company/work-day").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    @DisplayName("without payroll, every screen that shows pay is closed, but private details stay open")
    void payroll_off_closes_every_pay_screen() throws Exception {
        Session owner = onboardOwner("Smallco", "owner@smallco.test", PW);
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        switchOff("Smallco", "PAYROLL");

        for (String path : new String[] {
                "/api/v1/payroll/run",
                "/api/v1/people/employees/" + employeeId + "/compensation",
                "/api/v1/people/employees/" + employeeId + "/payslip",
                "/api/v1/people/employees/" + employeeId + "/settlement",
                "/api/v1/people/me/compensation",
                "/api/v1/people/me/payslip",
                "/api/v1/tax/me/declaration"}) {
            mockMvc.perform(get(path).header("Authorization", "Bearer " + owner.accessToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.feature").value("PAYROLL"));
        }
        // PAN, bank and UAN are HR records, not pay.
        mockMvc.perform(get("/api/v1/people/employees/" + employeeId + "/finance")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/people/attendance/me/today").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("without shifts, the working day is still set from company settings")
    void shifts_off_leaves_the_working_day_reachable() throws Exception {
        Session owner = onboardOwner("Shiftless", "owner@shiftless.test", PW);
        switchOff("Shiftless", "SHIFTS");

        mockMvc.perform(get("/api/v1/shifts/work-day").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());
        JsonNode day = workDay(owner);
        Map<String, Object> body = new HashMap<>();
        body.put("workDayMinutes", 480);
        body.put("workDayStart", "10:00");
        body.put("absentGraceMinutes", day.get("absentGraceMinutes").asInt());
        mockMvc.perform(put("/api/v1/company/work-day")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workDayMinutes").value(480))
                .andExpect(jsonPath("$.workDayStart").value("10:00"));
    }

    @Test
    @DisplayName("a company that does not track attendance here is never marked absent, so pay is not cut")
    void attendance_tracking_off_marks_nobody_absent() throws Exception {
        Session owner = onboardOwner("Payonly", "owner@payonly.test", PW);
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        // Employed for two months, with the absence rules on for the last one and nobody ever checking in.
        getJson("/api/v1/company/settings", owner);
        asTenant(owner, "update employees set start_date = ? where company_id = ?", today.minusDays(60));
        asTenant(owner, "update company_settings set attendance_rules_from = ? where company_id = ?", today.minusDays(30));
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", 1_200_000,
                                "effectiveDate", today.minusDays(60).toString(), "reason", "initial"))))
                .andExpect(status().isOk());

        // A recent past working day on which the rules mark them absent.
        LocalDate absentDay = null;
        for (LocalDate d = today.minusDays(1); d.isAfter(today.minusDays(25)); d = d.minusDays(1)) {
            if (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (getJson("/api/v1/people/attendance/day?date=" + d, owner).get("absent").asLong() == 1) {
                absentDay = d;
                break;
            }
        }
        assertThat(absentDay).as("the rules mark somebody with no check-in absent").isNotNull();
        String month = absentDay.toString().substring(0, 7);
        assertThat(getJson("/api/v1/people/employees/" + employeeId + "/payslip?month=" + month, owner)
                .get("lopDays").asDouble()).as("with tracking on, missing check-ins cost pay").isPositive();

        JsonNode day = workDay(owner);
        assertThat(day.get("trackAttendance").asBoolean()).isTrue();
        Map<String, Object> off = new HashMap<>();
        off.put("workDayMinutes", day.get("workDayMinutes").asInt());
        off.put("workDayStart", day.get("workDayStart").asText());
        off.put("absentGraceMinutes", day.get("absentGraceMinutes").asInt());
        off.put("trackAttendance", false);
        mockMvc.perform(put("/api/v1/company/work-day")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(off)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackAttendance").value(false))
                .andExpect(jsonPath("$.trackAttendanceFrom").doesNotExist());

        assertThat(getJson("/api/v1/people/attendance/day?date=" + absentDay, owner).get("absent").asLong()).isZero();
        assertThat(getJson("/api/v1/people/employees/" + employeeId + "/payslip?month=" + month, owner)
                .get("lopDays").asDouble()).as("with tracking off, nothing is deducted").isZero();

        // Switching it back on starts from today: days already gone stay paid.
        off.put("trackAttendance", true);
        mockMvc.perform(put("/api/v1/company/work-day")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(off)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackAttendanceFrom").value(today.toString()));
        assertThat(getJson("/api/v1/people/attendance/day?date=" + absentDay, owner).get("absent").asLong()).isZero();
    }

    @Test
    @DisplayName("the plans run from attendance alone to the whole product, and the old ones are retired")
    void plans_by_company_size() throws Exception {
        Session platform = platformOwner();
        Map<String, JsonNode> plans = new HashMap<>();
        for (JsonNode p : getJson("/api/v1/platform/plans", platform)) plans.put(p.get("code").asText(), p);

        assertThat(plans.get("ATTENDANCE").toString()).doesNotContain("\"PAYROLL\"");
        assertThat(plans.get("PAYROLL").toString()).contains("\"PAYROLL\"").doesNotContain("RECRUITMENT");
        assertThat(plans.get("GROWTH_2026").toString()).contains("RECRUITMENT", "EXPENSES").doesNotContain("PERFORMANCE");
        assertThat(plans.get("COMPLETE_2026").toString()).contains("PERFORMANCE", "ANALYTICS", "ASSISTANT");
        for (String code : new String[] {"ATTENDANCE", "PAYROLL", "GROWTH_2026", "COMPLETE_2026"}) {
            assertThat(plans.get(code).toString()).doesNotContain("STATUTORY_PAYROLL", "INCOME_TAX");
            assertThat(plans.get(code).get("active").asBoolean()).isTrue();
        }
        for (String old : new String[] {"ESSENTIALS", "GROWTH", "COMPLETE"}) {
            assertThat(plans.get(old).get("active").asBoolean()).as(old + " retired").isFalse();
        }
    }

    @Test
    @DisplayName("no two modules claim the same address")
    void every_address_has_at_most_one_module() {
        for (Feature a : Feature.values()) {
            for (String path : a.paths()) {
                String sample = path.replace("/**", "/x").replace("*", "id");
                assertThat(Feature.guarding(sample)).as(sample).isEqualTo(a);
            }
        }
        assertThat(Feature.guarding("/api/v1/people/employees/abc/finance")).isNull();
        assertThat(Feature.guarding("/api/v1/people/attendance/me/today")).isNull();
        assertThat(Feature.guarding("/api/v1/payroll")).isEqualTo(Feature.PAYROLL);
    }
}
