package com.calyvora.helpdesk;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** HR Helpdesk: raising, the HR queue, the conversation thread, status flow, and visibility. */
class HelpdeskIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    void the_demo_seeds_tickets_across_statuses() throws Exception {
        seedDemo();
        Session hr = login("leo.martins@northwind.demo", PW);   // HR agent
        JsonNode queue = getJson("/api/v1/helpdesk/tickets", hr);
        assertThat(queue.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    void an_employee_raises_a_ticket_hr_replies_and_resolves() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", PW);
        String id = postJson(priya, "/api/v1/helpdesk/tickets",
                Map.of("category", "IT", "subject", "New monitor request", "priority", "LOW")).get("id").asText();

        // The raiser sees it in "mine".
        assertThat(getJson("/api/v1/helpdesk/tickets/mine", priya).size()).isGreaterThanOrEqualTo(1);

        // HR replies and resolves.
        Session hr = login("leo.martins@northwind.demo", PW);
        postJson(hr, "/api/v1/helpdesk/tickets/" + id + "/comments", Map.of("body", "Ordered — arrives Monday."));
        postJson(hr, "/api/v1/helpdesk/tickets/" + id, Map.of("status", "RESOLVED"));   // PATCH via override below

        JsonNode t = getJson("/api/v1/helpdesk/tickets/" + id, hr);
        assertThat(t.get("status").asText()).isEqualTo("RESOLVED");
        assertThat(getJson("/api/v1/helpdesk/tickets/" + id + "/comments", priya).size()).isEqualTo(1);
    }

    @Test
    void an_employee_cannot_see_someone_elses_ticket_but_the_queue_is_agent_only() throws Exception {
        seedDemo();
        Session priya = login("priya.nair@northwind.demo", PW);
        String id = postJson(priya, "/api/v1/helpdesk/tickets",
                Map.of("category", "HR", "subject", "Private matter")).get("id").asText();

        Session sara = login("sara.okoro@northwind.demo", PW);   // another member
        mockMvc.perform(get("/api/v1/helpdesk/tickets/" + id).header("Authorization", "Bearer " + sara.accessToken()))
                .andExpect(status().isForbidden());
        // The queue is HR/admin only.
        mockMvc.perform(get("/api/v1/helpdesk/tickets").header("Authorization", "Bearer " + sara.accessToken()))
                .andExpect(status().isForbidden());
    }

    // ---- helpers ----

    private void seedDemo() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private JsonNode postJson(Session s, String path, Map<String, ?> body) throws Exception {
        // Ticket status changes go through PATCH; everything else here is POST.
        boolean isPatch = path.matches(".*/helpdesk/tickets/[0-9a-f-]+$");
        var req = (isPatch
                ? org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path)
                : post(path))
                .header("Authorization", "Bearer " + s.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(json(body));
        MvcResult res = mockMvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        String content = res.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }
}
