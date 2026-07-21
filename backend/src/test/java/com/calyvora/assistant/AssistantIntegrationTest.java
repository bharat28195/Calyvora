package com.calyvora.assistant;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The cross-app assistant answers from the tenant's real data. With no API key configured (as in
 * tests) it runs the offline grounded provider — which must never fabricate and must stay tenant-scoped.
 */
class AssistantIntegrationTest extends IntegrationTestBase {

    private Session demoOwner() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login("ava.chen@northwind.demo", "demopass123");
    }

    private JsonNode ask(Session s, String question) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/assistant/ask")
                        .header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("question", question))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void answers_count_questions_from_real_metrics() throws Exception {
        Session owner = demoOwner();
        JsonNode res = ask(owner, "How many open tickets do we have?");
        assertThat(res.get("mode").asText()).isEqualTo("local");
        // The seed creates 3 open tickets.
        assertThat(res.get("answer").asText()).contains("3");
    }

    @Test
    void answers_knowledge_questions_with_grounded_sources() throws Exception {
        Session owner = demoOwner();
        JsonNode res = ask(owner, "How does our authentication and key rotation work?");
        // Should surface the auth handbook page as a source and quote it.
        assertThat(res.get("answer").asText()).containsIgnoringCase("rotation");
        assertThat(res.get("sources")).isNotEmpty();
        assertThat(res.get("sources").get(0).get("kind").asText()).isEqualTo("page");
    }

    @Test
    void is_tenant_scoped() throws Exception {
        demoOwner();   // seeds Northwind
        Session outsider = onboardOwner("Acme", "owner@acme.com", "password1234");
        JsonNode res = ask(outsider, "How many team members are there?");
        // The outsider is a company of one — must not see Northwind's 6.
        assertThat(res.get("answer").asText()).contains("1");
    }
}
