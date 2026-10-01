package com.calyvora.trial;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Start free trial" as a request the vendor grants, not a signup (PD-21).
 *
 * <p>This class deliberately re-declares {@code @SpringBootTest} <b>without</b> the base class's
 * {@code registration.open=true} override, so it runs against the shipped defaults. The single most
 * important assertion in the suite is the first one: on a default deployment, the public register
 * endpoint creates nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = ZONKY, refresh = AFTER_EACH_TEST_METHOD)
@Import(com.calyvora.support.RecordingEmailService.class)
class TrialRequestFlowTest extends IntegrationTestBase {

    private static final String ASKER = "meera@acme-textiles.test";

    @Test
    @DisplayName("with the shipped config, nobody can create a workspace from the public endpoint")
    void self_signup_is_closed_by_default() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Walk In Ltd", "firstName", "Walk",
                                "lastName", "In", "email", "walkin@nowhere.test", "password", "Passw0rd!23"))))
                .andExpect(status().isForbidden());

        // And the refusal is total: no half-made account is left behind to log in with later.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "walkin@nowhere.test", "password", "Passw0rd!23"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a trial request is recorded, the vendor is emailed, and the asker is acknowledged")
    void submitting_notifies_the_vendor() throws Exception {
        submit(ASKER).andExpect(status().isAccepted())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.received").value(true));

        assertThat(email().trialNotifications()).hasSize(1);
        assertThat(email().trialNotifications().get(0).url()).contains("Acme Textiles", ASKER);
        assertThat(email().trialAcknowledgements()).hasSize(1);
        assertThat(email().trialAcknowledgements().get(0).to()).isEqualTo(ASKER);
    }

    @Test
    @DisplayName("clicking the button twice leaves one row in the queue, not two")
    void repeat_submissions_collapse() throws Exception {
        submit(ASKER).andExpect(status().isAccepted());
        submit(ASKER).andExpect(status().isAccepted());

        assertThat(queue()).hasSize(1);
        // The vendor is told once. A second alert about a request already sitting in the queue is
        // noise, and noise is how a real enquiry gets missed.
        assertThat(email().trialNotifications()).hasSize(1);
    }

    @Test
    @DisplayName("a trial request creates nothing that can be signed in to")
    void a_request_is_not_an_account() throws Exception {
        submit(ASKER).andExpect(status().isAccepted());

        // Every password anyone might have typed on the form — there wasn't one, and there is no user.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", ASKER, "password", "Passw0rd!23"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the queue is the vendor's: anonymous callers and customers can't read it")
    void the_queue_is_private() throws Exception {
        submit(ASKER).andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/platform/trial-requests")).andExpect(status().isUnauthorized());

        Session customer = provisionCustomer("Northwind Robotics", "admin@northwind.test");
        mockMvc.perform(get("/api/v1/platform/trial-requests")
                        .header("Authorization", "Bearer " + customer.accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("approval provisions the workspace, and only then does the login work")
    void approval_is_what_grants_access() throws Exception {
        submit(ASKER).andExpect(status().isAccepted());
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String id = queue().get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/platform/trial-requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "TrialPass@123", "seats", 10, "months", 1))))
                .andExpect(status().isOk());

        // The workspace exists on the terms the owner set, and the person who asked can now get in.
        Session customer = login(ASKER, "TrialPass@123");
        assertThat(customer.accessToken()).isNotBlank();
        assertThat(email().trialApprovals()).hasSize(1);
        assertThat(queue().get(0).get("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("declining leaves no account, and a decided request can't be acted on twice")
    void declining_closes_the_request() throws Exception {
        submit(ASKER).andExpect(status().isAccepted());
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        String id = queue().get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/platform/trial-requests/" + id + "/decline")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
        assertThat(queue().get(0).get("status").asText()).isEqualTo("DECLINED");

        mockMvc.perform(post("/api/v1/platform/trial-requests/" + id + "/approve")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("password", "TrialPass@123", "seats", 10, "months", 1))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", ASKER, "password", "TrialPass@123"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("someone who was turned down may ask again later")
    void a_declined_asker_can_come_back() throws Exception {
        submit(ASKER).andExpect(status().isAccepted());
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        mockMvc.perform(post("/api/v1/platform/trial-requests/" + queue().get(0).get("id").asText() + "/decline")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());

        // The "one open request per address" rule is partial on purpose: it stops a double-click, not
        // a customer whose circumstances changed six months later.
        submit(ASKER).andExpect(status().isAccepted());
        assertThat(queue()).hasSize(2);
    }

    @Test
    @DisplayName("the form still has to be a form")
    void the_payload_is_validated() throws Exception {
        Map<String, String> noEmail = new HashMap<>();
        noEmail.put("companyName", "Acme Textiles");
        noEmail.put("contactName", "Meera");
        mockMvc.perform(post("/api/v1/trial-requests")
                        .contentType(MediaType.APPLICATION_JSON).content(json(noEmail)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/trial-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", "Acme Textiles", "contactName", "Meera",
                                "email", "not-an-address"))))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private org.springframework.test.web.servlet.ResultActions submit(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/trial-requests")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("companyName", "Acme Textiles", "contactName", "Meera Nair",
                        "email", email, "phone", "+91 98000 00000", "teamSize", "20-50",
                        "note", "We run three shifts.", "source", "orbit-home"))));
    }

    /** The vendor's queue, read as the platform owner — the only account that may. */
    private JsonNode queue() throws Exception {
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        return getJson("/api/v1/platform/trial-requests", owner);
    }

    /** A customer company created the way the vendor really creates one, since signup is closed. */
    private Session provisionCustomer(String name, String adminEmail) throws Exception {
        Session owner = login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD);
        MvcResult created = mockMvc.perform(post("/api/v1/platform/companies")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", name, "adminFirstName", "Ada",
                                "adminLastName", "Admin", "adminEmail", adminEmail,
                                "password", "AdminPass@123", "seats", 25, "months", 12))))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(created.getResponse().getContentAsString()).contains(name);
        return login(adminEmail, "AdminPass@123");
    }
}
