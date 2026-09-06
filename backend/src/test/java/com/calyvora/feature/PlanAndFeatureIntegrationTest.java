package com.calyvora.feature;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Plans, per-company overrides, and the guard that makes them mean something.
 *
 * <p>The tests that matter most are the ones proving <b>enforcement</b>. A plan the customer can
 * bypass by typing a URL is not a plan, it is a suggestion — and a screen hidden by the frontend
 * while the API stays open is exactly the mistake the subscription lock made before it was fixed
 * server-side.
 */
class PlanAndFeatureIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    private Session platformOwner() throws Exception {
        platformOwner.ensurePlatformOwner();
        return login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
    }

    private String companyIdOf(Session owner, String name) throws Exception {
        for (JsonNode c : getJson("/api/v1/platform/companies", owner)) {
            if (name.equals(c.get("name").asText())) {
                return c.get("companyId").asText();
            }
        }
        throw new AssertionError("no company called " + name);
    }

    private void setPlan(Session platform, String companyId, String planCode) throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("planCode", planCode);   // HashMap, because Map.of rejects a null value
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/plan")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private void override(Session platform, String companyId, String feature, Boolean enabled) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("feature", feature);
        if (enabled != null) {
            body.put("enabled", enabled);
        }
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/features")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk());
    }

    private JsonNode featureState(Session owner, String feature) throws Exception {
        for (JsonNode f : getJson("/api/v1/company/features", owner)) {
            if (feature.equals(f.get("feature").asText())) {
                return f;
            }
        }
        throw new AssertionError("no feature " + feature);
    }

    // ---- defaults ----

    @Test
    void a_company_with_no_plan_keeps_everything_it_had() throws Exception {
        // The migration's promise. Every module defaults on, so nobody who predates plans loses a
        // screen the day plans arrive.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        assertThat(featureState(owner, "RECRUITMENT").get("enabled").asBoolean()).isTrue();
        assertThat(featureState(owner, "RECRUITMENT").get("source").asText()).isEqualTo("DEFAULT");
        // ...except the one that is trusted gradually.
        assertThat(featureState(owner, "STATUTORY_PAYROLL").get("enabled").asBoolean()).isFalse();

        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    // ---- plans ----

    @Test
    void the_seeded_plans_are_there_and_differ() throws Exception {
        Session platform = platformOwner();
        JsonNode plans = getJson("/api/v1/platform/plans", platform);

        assertThat(plans).hasSizeGreaterThanOrEqualTo(3);
        JsonNode essentials = null, complete = null;
        for (JsonNode p : plans) {
            if ("ESSENTIALS".equals(p.get("code").asText())) essentials = p;
            if ("COMPLETE".equals(p.get("code").asText())) complete = p;
        }
        assertThat(essentials).isNotNull();
        assertThat(complete).isNotNull();
        assertThat(essentials.get("features").size()).isLessThan(complete.get("features").size());
    }

    @Test
    void a_plan_turns_off_what_it_does_not_include() throws Exception {
        // A plan is authoritative in BOTH directions. A feature it omits is off, not merely
        // unmentioned — otherwise "Essentials" would cost less and include everything.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session platform = platformOwner();
        setPlan(platform, companyIdOf(platform, "Acme"), "ESSENTIALS");

        JsonNode recruitment = featureState(owner, "RECRUITMENT");
        assertThat(recruitment.get("enabled").asBoolean()).isFalse();
        assertThat(recruitment.get("source").asText()).isEqualTo("PLAN");
        assertThat(recruitment.get("planCode").asText()).isEqualTo("ESSENTIALS");

        assertThat(featureState(owner, "PAYROLL").get("enabled").asBoolean()).isTrue();
    }

    @Test
    void the_api_refuses_a_module_the_plan_does_not_include() throws Exception {
        // THE TEST THAT MAKES PLANS REAL. Hiding a nav entry while the API stays open would leave the
        // module one typed URL away.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session platform = platformOwner();
        setPlan(platform, companyIdOf(platform, "Acme"), "ESSENTIALS");

        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.feature").value("RECRUITMENT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not included in your plan")));

        // Payroll is in Essentials, so it still answers.
        mockMvc.perform(get("/api/v1/payroll/run").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void upgrading_a_plan_opens_the_module_immediately() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session platform = platformOwner();
        String companyId = companyIdOf(platform, "Acme");

        setPlan(platform, companyId, "ESSENTIALS");
        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());

        setPlan(platform, companyId, "COMPLETE");
        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    // ---- overrides ----

    @Test
    void a_company_override_beats_the_plan_in_both_directions() throws Exception {
        // A promise made in a sales call outranks a table: "Essentials, but they also get recruitment"
        // has to be expressible without inventing a plan for one customer.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session platform = platformOwner();
        String companyId = companyIdOf(platform, "Acme");
        setPlan(platform, companyId, "ESSENTIALS");

        override(platform, companyId, "RECRUITMENT", true);
        assertThat(featureState(owner, "RECRUITMENT").get("source").asText()).isEqualTo("COMPANY");
        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        // And the other way: off, despite the plan including payroll.
        override(platform, companyId, "PAYROLL", false);
        mockMvc.perform(get("/api/v1/payroll/run").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void clearing_an_override_puts_the_company_back_on_its_plan() throws Exception {
        // Sending no "enabled" clears rather than meaning false. Without this, "undo that" would be
        // impossible to express and an owner would have to remember what the plan said.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session platform = platformOwner();
        String companyId = companyIdOf(platform, "Acme");
        setPlan(platform, companyId, "ESSENTIALS");
        override(platform, companyId, "RECRUITMENT", true);
        assertThat(featureState(owner, "RECRUITMENT").get("source").asText()).isEqualTo("COMPANY");

        override(platform, companyId, "RECRUITMENT", null);

        JsonNode after = featureState(owner, "RECRUITMENT");
        assertThat(after.get("source").asText()).isEqualTo("PLAN");
        assertThat(after.get("enabled").asBoolean()).isFalse();
    }

    @Test
    void taking_a_company_off_a_plan_restores_the_defaults() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session platform = platformOwner();
        String companyId = companyIdOf(platform, "Acme");
        setPlan(platform, companyId, "ESSENTIALS");
        assertThat(featureState(owner, "RECRUITMENT").get("enabled").asBoolean()).isFalse();

        setPlan(platform, companyId, null);

        JsonNode after = featureState(owner, "RECRUITMENT");
        assertThat(after.get("source").asText()).isEqualTo("DEFAULT");
        assertThat(after.get("enabled").asBoolean()).isTrue();
    }

    // ---- isolation and authority ----

    @Test
    void a_plan_on_one_company_leaves_another_alone() throws Exception {
        Session acme = onboardOwner("Acme", "owner@acme.com", PW);
        Session beta = onboardOwner("Beta Corp", "owner@beta.com", PW);
        Session platform = platformOwner();
        setPlan(platform, companyIdOf(platform, "Acme"), "ESSENTIALS");

        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", "Bearer " + acme.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/recruit/jobs").header("Authorization", "Bearer " + beta.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void a_customer_can_see_their_own_features_but_not_the_catalogue_or_the_switches() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String companyId = companyIdOf(platformOwner(), "Acme");

        assertThat(getJson("/api/v1/company/features", owner)).isNotEmpty();

        mockMvc.perform(get("/api/v1/platform/plans").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/plan")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", "COMPLETE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void the_login_and_subscription_surface_is_never_gated() throws Exception {
        // Guarding these would lock somebody out of a product they are still paying for. The filter
        // only ever runs once a tenant is bound and only on paths a feature actually claims.
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        Session platform = platformOwner();
        String companyId = companyIdOf(platform, "Acme");
        setPlan(platform, companyId, "ESSENTIALS");
        for (Feature f : Feature.values()) {
            override(platform, companyId, f.name(), false);
        }

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/subscription/me").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/people/employees").header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    // ---- editing the catalogue ----

    @Test
    void a_plan_can_be_created_and_its_features_replaced_wholesale() throws Exception {
        Session platform = platformOwner();

        mockMvc.perform(post("/api/v1/platform/plans")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "tiny", "name", "Tiny",
                                "pricePerEmployee", 49, "features", List.of("PAYROLL")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("TINY"))
                .andExpect(jsonPath("$.features[0]").value("PAYROLL"));

        // The list is replaced, not merged — which is what a checkbox grid produces.
        mockMvc.perform(patch("/api/v1/platform/plans/TINY")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("features", List.of("FEED")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1))
                .andExpect(jsonPath("$.features[0]").value("FEED"));
    }

    @Test
    void an_unknown_feature_in_a_plan_is_refused_by_name() throws Exception {
        Session platform = platformOwner();

        mockMvc.perform(post("/api/v1/platform/plans")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "ODD", "name", "Odd",
                                "features", List.of("TELEPORTATION")))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_retired_plan_cannot_be_assigned_to_anybody_new() throws Exception {
        Session platform = platformOwner();
        onboardOwner("Acme", "owner@acme.com", PW);
        String companyId = companyIdOf(platform, "Acme");

        mockMvc.perform(patch("/api/v1/platform/plans/ESSENTIALS")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("active", false))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/plan")
                        .header("Authorization", "Bearer " + platform.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", "ESSENTIALS"))))
                .andExpect(status().isBadRequest());
    }
}
