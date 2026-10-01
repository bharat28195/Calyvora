package com.calyvora.shift;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Shift scheduling: templates, the weekly roster, one-shift-per-day upsert, and RBAC. */
class ShiftIntegrationTest extends IntegrationTestBase {

    private static final String DEMO_PW = "demopass123";

    @Test
    void the_demo_seeds_shift_templates_and_a_populated_roster() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        JsonNode shifts = getJson("/api/v1/shifts", owner);
        assertThat(shifts.size()).isEqualTo(3);

        JsonNode roster = getJson("/api/v1/shifts/roster", owner);
        assertThat(roster.get("days").size()).isEqualTo(7);
        assertThat(roster.get("employees").size()).isGreaterThanOrEqualTo(6);
        // Sara (3 mornings) + Tom (5 evenings) = 8 seeded assignments this week.
        assertThat(roster.get("assignments").size()).isGreaterThanOrEqualTo(8);
    }

    @Test
    void assigning_twice_on_a_day_moves_rather_than_duplicates() throws Exception {
        seedDemo();
        Session owner = login("ava.chen@northwind.demo", DEMO_PW);

        String shiftA = postJson(owner, "/api/v1/shifts",
                Map.of("name", "A", "startTime", "08:00", "endTime", "16:00")).get("id").asText();
        String shiftB = postJson(owner, "/api/v1/shifts",
                Map.of("name", "B", "startTime", "16:00", "endTime", "00:00")).get("id").asText();

        // Pick any employee off the roster.
        JsonNode roster = getJson("/api/v1/shifts/roster", owner);
        String employeeId = roster.get("employees").get(0).get("employeeId").asText();
        String day = LocalDate.now().toString();

        postJson(owner, "/api/v1/shifts/roster/assign",
                Map.of("employeeId", employeeId, "onDate", day, "shiftId", shiftA));
        postJson(owner, "/api/v1/shifts/roster/assign",
                Map.of("employeeId", employeeId, "onDate", day, "shiftId", shiftB));

        // Still exactly one assignment for that employee/day, now pointing at shift B.
        JsonNode after = getJson("/api/v1/shifts/roster", owner);
        long forDay = 0;
        String resolvedShift = null;
        for (JsonNode a : after.get("assignments")) {
            if (a.get("employeeId").asText().equals(employeeId) && a.get("onDate").asText().equals(day)) {
                forDay++;
                resolvedShift = a.get("shiftId").asText();
            }
        }
        assertThat(forDay).isEqualTo(1);
        assertThat(resolvedShift).isEqualTo(shiftB);
    }

    @Test
    void shifts_are_admin_only() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", DEMO_PW);
        mockMvc.perform(get("/api/v1/shifts").header("Authorization", "Bearer " + priya.accessToken()))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private JsonNode postJson(Session s, String path, Map<String, ?> body) throws Exception {
        MvcResult res = mockMvc.perform(post(path).header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful()).andReturn();
        String content = res.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
