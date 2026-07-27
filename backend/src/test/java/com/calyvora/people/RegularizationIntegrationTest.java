package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Attendance regularization: raise → the manager approves → the day is marked present. */
class RegularizationIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    void a_manager_approves_a_report_regularization_and_the_day_is_marked_present() throws Exception {
        seedDemo();
        // Sara's seeded request is pending for her manager, Tom (a MANAGER).
        Session tom = login("tom.becker@northwind.demo", PW);
        JsonNode pending = getJson("/api/v1/attendance/regularizations/pending", tom);
        assertThat(pending.size()).isGreaterThanOrEqualTo(1);
        String id = pending.get(0).get("id").asText();
        String date = pending.get(0).get("date").asText();

        postJson(tom, "/api/v1/attendance/regularizations/" + id + "/approve", Map.of("note", "ok"));

        // Sara's attendance for that day is now PRESENT with the requested times.
        Session sara = login("sara.okoro@northwind.demo", PW);
        JsonNode month = getJson("/api/v1/people/attendance/me?month=" + date.substring(0, 7), sara);
        boolean present = false;
        for (JsonNode d : month.get("days")) {
            if (d.get("date").asText().equals(date)) {
                present = "PRESENT".equals(d.path("status").asText());
            }
        }
        assertThat(present).isTrue();
    }

    @Test
    void an_employee_raises_a_request_and_sees_it_in_mine_but_a_non_manager_sees_no_queue() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", PW);
        postJson(priya, "/api/v1/attendance/regularizations", Map.of(
                "date", LocalDate.now().minusDays(2).toString(), "checkIn", "09:15", "checkOut", "18:00",
                "reason", "forgot to punch"));
        assertThat(getJson("/api/v1/attendance/regularizations/mine", priya).size()).isGreaterThanOrEqualTo(1);
        // Priya manages nobody, so her approval queue is empty.
        assertThat(getJson("/api/v1/attendance/regularizations/pending", priya).size()).isEqualTo(0);
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
