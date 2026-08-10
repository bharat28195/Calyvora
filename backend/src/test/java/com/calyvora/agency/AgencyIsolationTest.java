package com.calyvora.agency;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The agency tier (PD-18) exists to give a customer running several companies a console of their own,
 * instead of the platform console — which reads every tenant on the system and can switch billing on.
 *
 * <p>Two things must hold, and the tests here attack both. An agency sees <em>only</em> its own
 * companies, and only their summaries. And an agency can provision a company but never activate it —
 * selling stays with the vendor.
 */
class AgencyIsolationTest extends IntegrationTestBase {

    private static final String PW = "agencypass123";
    private static final String OWNER = PLATFORM_OWNER_EMAIL;
    private static final String OWNER_PW = PLATFORM_OWNER_PASSWORD;

    @Test
    void an_agency_sees_only_its_own_companies() throws Exception {
        Session owner = login(OWNER, OWNER_PW);
        Session alpha = agencyWith(owner, "Alpha Group", "alpha@agency.test");
        Session beta = agencyWith(owner, "Beta Group", "beta@agency.test");

        addCompany(alpha, "Alpha Retail", "admin@alpharetail.test");
        addCompany(beta, "Beta Foods", "admin@betafoods.test");

        assertThat(names(getJson("/api/v1/agency/companies", alpha))).containsExactly("Alpha Retail");
        assertThat(names(getJson("/api/v1/agency/companies", beta))).containsExactly("Beta Foods");
    }

    @Test
    void one_agency_cannot_read_anothers_company_by_id() throws Exception {
        Session owner = login(OWNER, OWNER_PW);
        Session alpha = agencyWith(owner, "Alpha Group", "alpha2@agency.test");
        Session beta = agencyWith(owner, "Beta Group", "beta2@agency.test");
        String alphaCompanyId = addCompany(alpha, "Alpha Retail", "admin@alpharetail2.test");

        // 404 rather than 403 on purpose: a 403 would confirm the company exists, which is itself a
        // leak of one agency's book to another.
        mockMvc.perform(get("/api/v1/agency/companies/" + alphaCompanyId)
                        .header("Authorization", "Bearer " + beta.accessToken()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/agency/companies/" + alphaCompanyId + "/request-seats")
                        .header("Authorization", "Bearer " + beta.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seats", 50))))
                .andExpect(status().isNotFound());
    }

    @Test
    void an_agency_cannot_reach_the_platform_console_or_activate_billing() throws Exception {
        Session owner = login(OWNER, OWNER_PW);
        Session alpha = agencyWith(owner, "Alpha Group", "alpha3@agency.test");
        String companyId = addCompany(alpha, "Alpha Retail", "admin@alpharetail3.test");
        String auth = "Bearer " + alpha.accessToken();

        mockMvc.perform(get("/api/v1/platform/companies").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/platform/agencies").header("Authorization", auth))
                .andExpect(status().isForbidden());
        // The whole point of the split: provisioning is theirs, switching billing on is not.
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/renew")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("months", 12))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/end")
                        .header("Authorization", auth))
                .andExpect(status().isForbidden());
    }

    @Test
    void an_agency_cannot_read_the_hr_data_of_a_company_it_runs() throws Exception {
        Session owner = login(OWNER, OWNER_PW);
        Session alpha = agencyWith(owner, "Alpha Group", "alpha4@agency.test");
        addCompany(alpha, "Alpha Retail", "admin@alpharetail4.test");
        String auth = "Bearer " + alpha.accessToken();

        // The people-ops surface is role-gated and an agency holds none of those roles.
        mockMvc.perform(get("/api/v1/payroll/run").header("Authorization", auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/people/leave").header("Authorization", auth))
                .andExpect(status().isForbidden());

        // The directory is open to any authenticated user on purpose (colleagues can look each other
        // up), so this one answers 200 — but bound to the agency's OWN workspace, which is why the
        // member company's people are not in it. That tenant binding, not the role, is the isolation.
        JsonNode directory = getJson("/api/v1/people/employees", alpha);
        for (JsonNode e : directory) {
            assertThat(e.get("email").asText()).isNotEqualTo("admin@alpharetail4.test");
        }
    }

    @Test
    void a_company_an_agency_creates_is_locked_until_the_owner_activates_it() throws Exception {
        Session owner = login(OWNER, OWNER_PW);
        Session alpha = agencyWith(owner, "Alpha Group", "alpha5@agency.test");
        String companyId = addCompany(alpha, "Alpha Retail", "admin@alpharetail5.test");

        JsonNode created = getJson("/api/v1/agency/companies/" + companyId, alpha);
        assertThat(created.get("subscriptionStatus").asText()).isEqualTo("PENDING");
        assertThat(created.get("locked").asBoolean()).isTrue();

        // Its admin can sign in — they have to, to see why it is locked — but nothing else works.
        Session admin = login("admin@alpharetail5.test", PW);
        mockMvc.perform(get("/api/v1/people/employees").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isPaymentRequired());

        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/renew")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("months", 12))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

        Session unlocked = login("admin@alpharetail5.test", PW);
        mockMvc.perform(get("/api/v1/people/employees").header("Authorization", "Bearer " + unlocked.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void a_company_admin_cannot_reach_the_agency_console() throws Exception {
        Session owner = login(OWNER, OWNER_PW);
        // An ACTIVE company on purpose: an agency-created one is still locked, and the lock answers
        // 402 before the role check ever runs, which would test the wrong thing.
        mockMvc.perform(post("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Live Retail", "adminFirstName", "Ada",
                                "adminLastName", "Min", "adminEmail", "admin@liveretail.test",
                                "password", PW, "seats", 5, "months", 12))))
                .andExpect(status().isCreated());

        Session admin = login("admin@liveretail.test", PW);
        mockMvc.perform(get("/api/v1/agency/companies").header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void the_owner_console_shows_direct_and_agency_companies_together() throws Exception {
        Session owner = login(OWNER, OWNER_PW);
        Session alpha = agencyWith(owner, "Alpha Group", "alpha7@agency.test");
        addCompany(alpha, "Alpha Retail", "admin@alpharetail7.test");

        // Sold direct — no agency. Both kinds belong in one list; the agency name is what tells them
        // apart, and it is null for a direct sale.
        mockMvc.perform(post("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Solo Traders", "adminFirstName", "Sam",
                                "adminLastName", "Direct", "adminEmail", "admin@solo.test",
                                "password", PW, "seats", 5, "months", 12))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.agencyName").doesNotExist());

        JsonNode all = getJson("/api/v1/platform/companies", owner);
        assertThat(names(all)).contains("Alpha Retail", "Solo Traders");
        for (JsonNode c : all) {
            if (c.get("name").asText().equals("Alpha Retail")) {
                assertThat(c.get("agencyName").asText()).isEqualTo("Alpha Group");
            }
        }
        // The agency's own workspace is a company row, but it is not a customer and is never billed.
        assertThat(names(all)).doesNotContain("Alpha Group");
    }

    // ---- helpers ----

    private Session agencyWith(Session owner, String name, String ownerEmail) throws Exception {
        mockMvc.perform(post("/api/v1/platform/agencies")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("agencyName", name, "ownerFirstName", "Ann",
                                "ownerLastName", "Gency", "ownerEmail", ownerEmail, "password", PW))))
                .andExpect(status().isCreated());
        return login(ownerEmail, PW);
    }

    private String addCompany(Session agency, String name, String adminEmail) throws Exception {
        String body = mockMvc.perform(post("/api/v1/agency/companies")
                        .header("Authorization", "Bearer " + agency.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", name, "adminFirstName", "Ada",
                                "adminLastName", "Min", "adminEmail", adminEmail, "password", PW,
                                "seats", 5, "months", 12))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("companyId").asText();
    }

    private java.util.List<String> names(JsonNode list) {
        java.util.List<String> out = new java.util.ArrayList<>();
        list.forEach(c -> out.add(c.get("name").asText()));
        return out;
    }
}
