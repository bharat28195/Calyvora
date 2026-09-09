package com.calyvora.dev;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The scale seed builds a real org, not a flat list — checked here because the shape is the whole
 * point of it.
 *
 * <p>A flat company would make every downline either everybody or nobody, and would never exercise the
 * recursive walk in {@code OrgScope} that the app shell performs on every single page load. Seeding
 * 1,000 people into one flat level would look like a scale test and measure nothing.
 *
 * <p>Kept small here (120 people, no attendance) so the suite stays fast; the real run is against the
 * deployed app, where the numbers mean something.
 */
class ScaleSeedIntegrationTest extends IntegrationTestBase {

    @Test
    void the_scale_seed_builds_a_four_level_org_and_can_be_removed() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/dev/seed-scale?employees=120&attendanceDays=2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seeded = objectMapper.readTree(r.getResponse().getContentAsString());
        assertThat(seeded.get("headcount").asInt()).isEqualTo(120);
        assertThat(seeded.get("attendanceRows").asInt()).isPositive();

        Session admin = login(seeded.get("adminEmail").asText(), seeded.get("password").asText());

        // The admin sees the whole company, because that is what an admin is.
        JsonNode adminStanding = getJson("/api/v1/team/mine", admin);
        assertThat(adminStanding.get("totalCount").asInt())
                .as("the admin's downline is the whole company minus themselves")
                .isEqualTo(119);

        // A head is a MANAGER partway down: strictly fewer than the company, strictly more than their
        // own direct reports. That gap is the four-level shape, and it is what makes the transitive
        // walk do work.
        Session head = login(seeded.get("headEmail").asText(), seeded.get("password").asText());
        JsonNode headStanding = getJson("/api/v1/team/mine", head);
        int direct = headStanding.get("directCount").asInt();
        int total = headStanding.get("totalCount").asInt();
        assertThat(direct).isPositive();
        assertThat(total).isGreaterThan(direct);
        assertThat(total).isLessThan(119);

        // Removing it takes the tenant with it — a thousand invented people must never be one stray
        // click away from a customer demo.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/dev/seed-scale"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(java.util.Map.of("email", seeded.get("adminEmail").asText(),
                                "password", seeded.get("password").asText()))))
                .andExpect(status().isUnauthorized());
    }
}
