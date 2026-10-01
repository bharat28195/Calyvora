package com.calyvora.search;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Global search reaches across all three apps in one tenant-scoped call, and never crosses tenants.
 */
class SearchIntegrationTest extends IntegrationTestBase {

    private Session demoOwner() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login("ava.chen@northwind.demo", "demopass123");
    }

    @Test
    void finds_matches_across_people_work_and_knowledge() throws Exception {
        Session owner = demoOwner();

        // A person (People), the Atlas project (Work), and an auth doc (Knowledge).
        assertThat(kinds(owner, "priya")).contains("person");
        assertThat(kinds(owner, "atlas")).contains("project");
        assertThat(kinds(owner, "rotation")).contains("page");   // "…Key Rotation" page body/title
    }

    @Test
    void short_query_returns_nothing() throws Exception {
        Session owner = demoOwner();
        JsonNode res = getJson("/api/v1/search?q=a", owner);
        assertThat(res.get("total").asInt()).isZero();
    }

    @Test
    void search_is_tenant_scoped() throws Exception {
        demoOwner();   // seeds Northwind
        Session outsider = onboardOwner("Acme", "owner@acme.com", "password1234");
        // The outsider must not see Northwind's data.
        JsonNode res = getJson("/api/v1/search?q=atlas", outsider);
        assertThat(res.get("total").asInt()).isZero();
    }

    private java.util.List<String> kinds(Session s, String q) throws Exception {
        JsonNode res = getJson("/api/v1/search?q=" + q, s);
        var kinds = new java.util.ArrayList<String>();
        for (JsonNode group : res.get("groups")) {
            for (JsonNode hit : group.get("hits")) {
                kinds.add(hit.get("kind").asText());
            }
        }
        return kinds;
    }
}
