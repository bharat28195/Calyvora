package com.calyvora.platform;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The commercial terms have to bind on the server, not just in the UI. Both of these were previously
 * decoration: ending a subscription changed a flag the frontend chose to respect while the API kept
 * serving the tenant, and the seat limit was never consulted when handing out an invitation.
 */
class SubscriptionEnforcementTest extends IntegrationTestBase {

    private static final String PW = "demopass123";
    private static final String OWNER = "owner@priorityhr.app";

    @Test
    void a_company_whose_subscription_has_ended_can_no_longer_read_or_write_its_data() throws Exception {
        seedPlatform();
        Session owner = login(OWNER, PW);
        String companyId = companyIdOf(owner, "Acme Logistics");
        Session admin = login("admin@acme.demo", PW);

        // Working normally before the subscription is ended.
        mockMvc.perform(get("/api/v1/people/employees")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/end")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/people/employees")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_INACTIVE"));

        mockMvc.perform(post("/api/v1/people/departments")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Should not be allowed"))))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void a_locked_company_can_still_sign_in_and_read_why_it_is_locked() throws Exception {
        seedPlatform();
        Session owner = login(OWNER, PW);
        String companyId = companyIdOf(owner, "Acme Logistics");
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/end")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        // Signing in must keep working, or the app has no way to explain the lock to the customer.
        Session admin = login("admin@acme.demo", PW);
        mockMvc.perform(get("/api/v1/subscription/me")
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void an_invitation_beyond_the_seat_limit_is_refused_with_a_useful_message() throws Exception {
        seedPlatform();
        Session owner = login(OWNER, PW);
        String companyId = companyIdOf(owner, "Acme Logistics");
        Session admin = login("admin@acme.demo", PW);

        long headcount = getJson("/api/v1/subscription/me", admin).get("seatsUsed").asLong();
        // Exactly enough seats for the people already there, and not one more.
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/seats")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seats", headcount))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "one.too.many@acme.demo", "role", "MEMBER"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("SEAT_LIMIT_REACHED"));
    }

    @Test
    void a_pending_invitation_holds_its_seat_so_the_limit_cannot_be_oversubscribed() throws Exception {
        seedPlatform();
        Session owner = login(OWNER, PW);
        String companyId = companyIdOf(owner, "Acme Logistics");
        Session admin = login("admin@acme.demo", PW);

        long headcount = getJson("/api/v1/subscription/me", admin).get("seatsUsed").asLong();
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/seats")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seats", headcount + 1))))
                .andExpect(status().isOk());

        // The one spare seat goes to the first invitation...
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "first@acme.demo", "role", "MEMBER"))))
                .andExpect(status().isCreated());

        // ...and the second has nowhere to sit, even though nobody has accepted yet.
        mockMvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + admin.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "second@acme.demo", "role", "MEMBER"))))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void the_owner_console_refuses_nonsense_commercial_values_instead_of_silently_changing_them() throws Exception {
        seedPlatform();
        Session owner = login(OWNER, PW);
        String companyId = companyIdOf(owner, "Acme Logistics");
        String auth = "Bearer " + owner.accessToken();

        // Previously each of these answered 200 having stored something else, or nothing at all.
        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/seats")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seats", -5))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/seats")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("seats", 1))))   // below the seeded headcount
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/price")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("price", -100))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/platform/companies/" + companyId + "/renew")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("months", -12))))
                .andExpect(status().isBadRequest());
    }

    private void seedPlatform() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/dev/seed-platform")).andExpect(status().isOk());
    }

    private String companyIdOf(Session owner, String name) throws Exception {
        for (JsonNode c : getJson("/api/v1/platform/companies", owner)) {
            if (c.get("name").asText().equals(name)) {
                return c.get("companyId").asText();
            }
        }
        throw new AssertionError("No company named " + name);
    }
}
