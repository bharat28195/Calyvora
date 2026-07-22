package com.calyvora.client;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Clients module (feedback D1 ⭐): clients + requests, request rollups, search, tenant isolation. */
class ClientIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    void create_client_add_requests_and_track_open_count() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String auth = "Bearer " + owner.accessToken();

        MvcResult created = mockMvc.perform(post("/api/v1/clients").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Globex", "contactName", "Hank", "status", "ACTIVE"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Globex"))
                .andReturn();
        String clientId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        // add two requests
        mockMvc.perform(post("/api/v1/clients/" + clientId + "/requests").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "SSO with Okta"))))
                .andExpect(status().isCreated());
        MvcResult r2 = mockMvc.perform(post("/api/v1/clients/" + clientId + "/requests").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("title", "EU data residency"))))
                .andExpect(status().isCreated()).andReturn();
        String reqId = objectMapper.readTree(r2.getResponse().getContentAsString()).get("id").asText();

        // detail shows both requests, 2 open
        JsonNode detail = getJson("/api/v1/clients/" + clientId, owner);
        assertThat(detail.get("requests")).hasSize(2);
        assertThat(detail.get("client").get("openRequests").asInt()).isEqualTo(2);

        // deliver one → 1 open
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/clients/" + clientId + "/requests/" + reqId).header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("status", "DELIVERED"))))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/clients/" + clientId, owner).get("client").get("openRequests").asInt()).isEqualTo(1);
    }

    @Test
    void clients_are_tenant_isolated() throws Exception {
        Session a = onboardOwner("Company A", "a@a.com", PW);
        mockMvc.perform(post("/api/v1/clients").header("Authorization", "Bearer " + a.accessToken())
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "A-Client")))).andExpect(status().isCreated());

        Session b = onboardOwner("Company B", "b@b.com", PW);
        assertThat(getJson("/api/v1/clients", b).size()).isZero();   // B sees none of A's clients
    }

    @Test
    void clients_are_owner_admin_only_and_dont_leak_through_search() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session member = login("priya.nair@northwind.demo", "demopass123");   // MEMBER
        String auth = "Bearer " + member.accessToken();

        mockMvc.perform(get("/api/v1/clients").header("Authorization", auth)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/clients").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("name", "Sneaky"))))
                .andExpect(status().isForbidden());

        // the search box must not become a side door around the role gate
        JsonNode search = getJson("/api/v1/search?q=globex", member);
        for (JsonNode g : search.get("groups")) {
            for (JsonNode hit : g.get("hits")) {
                assertThat(hit.get("kind").asText()).isNotEqualTo("client");
            }
        }
    }

    @Test
    void seeded_demo_has_clients_and_they_are_searchable() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");
        assertThat(getJson("/api/v1/clients", owner).size()).isGreaterThanOrEqualTo(3);

        // global search surfaces a client
        JsonNode search = getJson("/api/v1/search?q=globex", owner);
        boolean hasClient = false;
        for (JsonNode g : search.get("groups")) {
            for (JsonNode hit : g.get("hits")) if ("client".equals(hit.get("kind").asText())) hasClient = true;
        }
        assertThat(hasClient).isTrue();
    }
}
