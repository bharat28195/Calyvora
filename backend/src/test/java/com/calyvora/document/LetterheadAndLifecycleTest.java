package com.calyvora.document;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The letterpad, and the two moments that produce letters by themselves (PD-20): somebody joining
 * and somebody leaving.
 *
 * <p>What these tests defend is mostly about <em>not losing things</em>. A PATCH that blanks the
 * address nobody noticed was there, a relieving letter issued while the laptop is still out, an exit
 * that starts twice — each is silent at the time and expensive later.
 */
class LetterheadAndLifecycleTest extends IntegrationTestBase {

    private static final String PW = "letterpass123";

    // ---- letterpad ----

    @Test
    void a_company_that_never_configured_a_letterpad_still_has_one() throws Exception {
        Session admin = onboardOwner("Northwind", "lh1@northwind.test", PW);

        // Not a 404: the first letter a company issues should already be headed properly.
        mockMvc.perform(get("/api/v1/documents/letterhead").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heading").value("Northwind"))
                .andExpect(jsonPath("$.fontFamily").value("SERIF"))
                .andExpect(jsonPath("$.brandColor").value("#7c5cff"));
    }

    @Test
    void editing_one_field_of_the_letterpad_leaves_the_rest_alone() throws Exception {
        Session admin = onboardOwner("Northwind", "lh2@northwind.test", PW);
        patchLetterhead(admin, Map.of(
                "addressLines", "42 MG Road\nBengaluru 560038",
                "footerText", "CIN U72900KA2019PTC000000",
                "brandColor", "#0f766e"));

        // The colour picker moving must not take the registered office off the footer with it.
        patchLetterhead(admin, Map.of("brandColor", "#b91c1c"));

        JsonNode after = getJson("/api/v1/documents/letterhead", admin);
        assertThat(after.get("brandColor").asText()).isEqualTo("#b91c1c");
        assertThat(after.get("addressLines").asText()).contains("Bengaluru");
        assertThat(after.get("footerText").asText()).contains("CIN");
    }

    @Test
    void a_colour_is_accepted_however_it_was_pasted_and_rejected_when_it_is_not_one() throws Exception {
        Session admin = onboardOwner("Northwind", "lh3@northwind.test", PW);

        // No hash, upper case, stray spaces — all the ways a hex code arrives from a brand document.
        patchLetterhead(admin, Map.of("brandColor", " 0F766E "));
        assertThat(getJson("/api/v1/documents/letterhead", admin).get("brandColor").asText())
                .isEqualTo("#0f766e");

        mockMvc.perform(patch("/api/v1/documents/letterhead")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("brandColor", "cornflower"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/documents/letterhead")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fontFamily", "COMIC"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void one_companys_letterpad_is_invisible_to_another() throws Exception {
        Session northwind = onboardOwner("Northwind", "lh4@northwind.test", PW);
        Session acme = onboardOwner("Acme", "lh4@acme.test", PW);
        patchLetterhead(northwind, Map.of("heading", "Northwind Robotics Pvt Ltd"));

        assertThat(getJson("/api/v1/documents/letterhead", acme).get("heading").asText())
                .isEqualTo("Acme");
    }

    @Test
    void a_letter_records_how_it_was_headed_at_the_time_it_was_issued() throws Exception {
        Session admin = onboardOwner("Northwind", "lh5@northwind.test", PW);
        String templateId = firstTemplateOfKind(admin, "JOINING_LETTER");

        mockMvc.perform(patch("/api/v1/documents/templates/" + templateId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("useLetterhead", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.useLetterhead").value(false));

        JsonNode doc = objectMapper.readTree(mockMvc.perform(post("/api/v1/documents")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", templateId,
                                "overrides", Map.of("employee.fullName", "Dana Scully")))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        // Turning the letterpad back on for future letters must not re-head one already issued.
        assertThat(doc.get("useLetterhead").asBoolean()).isFalse();
        mockMvc.perform(patch("/api/v1/documents/templates/" + templateId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("useLetterhead", true))))
                .andExpect(status().isOk());
        assertThat(getJson("/api/v1/documents/" + doc.get("id").asText(), admin)
                .get("useLetterhead").asBoolean()).isFalse();
    }

    // ---- exits ----

    @Test
    void starting_an_exit_puts_them_on_notice_and_raises_the_clearance_list() throws Exception {
        Session admin = onboardOwner("Northwind", "x1@northwind.test", PW);
        String employeeId = anEmployee(admin);

        JsonNode exit = startExit(admin, employeeId, LocalDate.now().plusMonths(1));
        assertThat(exit.get("employmentStatus").asText()).isEqualTo("NOTICE");
        assertThat(exit.get("tasksTotal").asInt()).isGreaterThan(0);
        assertThat(exit.get("tasksDone").asInt()).isZero();
        assertThat(exit.get("checklistComplete").asBoolean()).isFalse();

        // The exits screen is the manager's queue, so they have to appear on it.
        assertThat(getJson("/api/v1/people/exits", admin)).hasSize(1);
    }

    @Test
    void an_exit_cannot_be_started_twice() throws Exception {
        Session admin = onboardOwner("Northwind", "x2@northwind.test", PW);
        String employeeId = anEmployee(admin);
        startExit(admin, employeeId, LocalDate.now().plusMonths(1));

        // Otherwise a second start would silently overwrite the agreed last working day and re-seed
        // a checklist someone has already been working through.
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/exit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lastWorkingDay", LocalDate.now().plusMonths(2).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void completing_an_exit_is_refused_while_clearance_is_outstanding() throws Exception {
        Session admin = onboardOwner("Northwind", "x3@northwind.test", PW);
        String employeeId = anEmployee(admin);
        startExit(admin, employeeId, LocalDate.now().plusDays(30));

        // A relieving letter certifies that property came back and dues were settled. Issuing it
        // before that is true is the failure this guard exists for.
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/exit/complete")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest());

        // Deliberate override, not a default.
        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/exit/complete?force=true")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("TERMINATED"));
    }

    @Test
    void working_the_checklist_through_lets_the_exit_complete_and_issues_both_letters() throws Exception {
        Session admin = onboardOwner("Northwind", "x4@northwind.test", PW);
        String employeeId = anEmployee(admin);
        JsonNode exit = startExit(admin, employeeId, LocalDate.now().plusDays(30));

        for (JsonNode task : exit.get("checklist")) {
            mockMvc.perform(patch("/api/v1/people/onboarding/" + task.get("id").asText())
                            .header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("completed", true))))
                    .andExpect(status().isOk());
        }

        JsonNode done = objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/people/employees/" + employeeId + "/exit/complete")
                                .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(done.get("employmentStatus").asText()).isEqualTo("TERMINATED");
        assertThat(done.get("checklistComplete").asBoolean()).isTrue();
        assertThat(kindsOf(done.get("letters")))
                .contains("RELIEVING_LETTER", "EXPERIENCE_LETTER");
    }

    @Test
    void a_withdrawn_resignation_leaves_no_trace() throws Exception {
        Session admin = onboardOwner("Northwind", "x5@northwind.test", PW);
        String employeeId = anEmployee(admin);
        startExit(admin, employeeId, LocalDate.now().plusDays(30));

        mockMvc.perform(delete("/api/v1/people/employees/" + employeeId + "/exit")
                        .header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employmentStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.lastWorkingDay").doesNotExist())
                .andExpect(jsonPath("$.tasksTotal").value(0));

        assertThat(getJson("/api/v1/people/exits", admin)).isEmpty();
    }

    @Test
    void the_last_working_day_cannot_precede_the_start_date() throws Exception {
        Session admin = onboardOwner("Northwind", "x6@northwind.test", PW);
        String employeeId = anEmployee(admin);
        mockMvc.perform(patch("/api/v1/people/employees/" + employeeId)
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("startDate", LocalDate.now().minusYears(1).toString()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/exit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("lastWorkingDay", LocalDate.now().minusYears(2).toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void a_missing_last_working_day_is_a_message_not_a_500() throws Exception {
        Session admin = onboardOwner("Northwind", "x7@northwind.test", PW);
        String employeeId = anEmployee(admin);

        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/exit")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new HashMap<String, Object>())))
                .andExpect(status().isBadRequest());
    }

    // ---- hiring ----

    @Test
    void making_an_offer_raises_the_letter_from_the_candidates_own_details() throws Exception {
        Session admin = onboardOwner("Northwind", "h1@northwind.test", PW);
        String candidateId = aCandidate(admin, "Dana Scully", "dana@example.test");

        JsonNode result = objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/recruit/candidates/" + candidateId + "/offer")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("jobTitle", "Senior Engineer",
                                        "startDate", LocalDate.now().plusMonths(1).toString(),
                                        "annualSalary", 1450000, "currency", "INR"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(result.get("candidate").get("stage").asText()).isEqualTo("OFFER");
        assertThat(result.get("letterNote").isNull()).isTrue();

        // A candidate has no employee row, so every value came from the request. The letter still has
        // to read as a letter — no dashes where the salary should be.
        String body = getJson("/api/v1/documents/" + result.get("documentId").asText(), admin)
                .get("body").asText();
        assertThat(body).contains("Dana").contains("Senior Engineer").contains("1,450,000");
    }

    @Test
    void hiring_invites_them_carries_the_role_onto_their_profile_and_raises_the_joining_letter()
            throws Exception {
        Session admin = onboardOwner("Northwind", "h2@northwind.test", PW);
        String candidateId = aCandidate(admin, "Marcus Kane", "marcus@example.test");
        String startDate = LocalDate.now().plusWeeks(2).toString();

        JsonNode hire = objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/recruit/candidates/" + candidateId + "/hire")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("role", "MEMBER", "jobTitle", "Technician",
                                        "startDate", startDate))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(hire.get("candidate").get("stage").asText()).isEqualTo("HIRED");
        assertThat(hire.get("joinLink").asText()).contains("/accept-invite?token=");
        assertThat(hire.get("documentId").isNull()).isFalse();

        // The agreed role waits on the invitation because an employee row needs a user, and the user
        // does not exist yet. Accepting, then a directory read, is what lands it.
        String token = hire.get("joinLink").asText().substring(hire.get("joinLink").asText().indexOf("token=") + 6);
        mockMvc.perform(post("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token, "firstName", "Marcus",
                                "lastName", "Kane", "password", PW))))
                .andExpect(status().isOk());

        JsonNode directory = getJson("/api/v1/people/employees", admin);
        JsonNode marcus = null;
        for (JsonNode e : directory) {
            if ("marcus@example.test".equals(e.get("email").asText())) marcus = e;
        }
        assertThat(marcus).as("the hired candidate appears in the directory").isNotNull();
        assertThat(marcus.get("jobTitle").asText()).isEqualTo("Technician");
        assertThat(marcus.get("startDate").asText()).isEqualTo(startDate);
        assertThat(marcus.get("employmentStatus").asText()).isEqualTo("ONBOARDING");

        // …and their joining checklist is already waiting, which is the point of the whole flow.
        assertThat(getJson("/api/v1/people/employees/" + marcus.get("id").asText() + "/onboarding", admin))
                .isNotEmpty();
    }

    @Test
    void a_candidate_with_no_email_cannot_be_hired() throws Exception {
        Session admin = onboardOwner("Northwind", "h3@northwind.test", PW);
        String candidateId = aCandidate(admin, "No Contact", null);

        // There would be nowhere to send the joining link, and the invitation is what gives them
        // a login at all.
        mockMvc.perform(post("/api/v1/recruit/candidates/" + candidateId + "/hire")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "MEMBER"))))
                .andExpect(status().isBadRequest());
    }

    // ---- helpers ----

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private void patchLetterhead(Session s, Map<String, Object> patch) throws Exception {
        mockMvc.perform(patch("/api/v1/documents/letterhead")
                        .header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(patch)))
                .andExpect(status().isOk());
    }

    /** The owner's own employee profile — provisioned on the first directory read. */
    private String anEmployee(Session admin) throws Exception {
        return getJson("/api/v1/people/employees", admin).get(0).get("id").asText();
    }

    private JsonNode startExit(Session admin, String employeeId, LocalDate lastDay) throws Exception {
        return objectMapper.readTree(mockMvc.perform(
                        post("/api/v1/people/employees/" + employeeId + "/exit")
                                .header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("lastWorkingDay", lastDay.toString(),
                                        "reason", "Resignation"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String aCandidate(Session admin, String name, String email) throws Exception {
        String jobId = objectMapper.readTree(mockMvc.perform(post("/api/v1/recruit/jobs")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Technician", "positions", 2))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        if (email != null) payload.put("email", email);
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/recruit/jobs/" + jobId + "/candidates")
                        .header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(payload)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String firstTemplateOfKind(Session admin, String kind) throws Exception {
        for (JsonNode t : getJson("/api/v1/documents/templates", admin)) {
            if (kind.equals(t.get("kind").asText())) return t.get("id").asText();
        }
        throw new AssertionError("No starter template of kind " + kind);
    }

    private java.util.List<String> kindsOf(JsonNode letters) {
        java.util.List<String> out = new java.util.ArrayList<>();
        letters.forEach(l -> out.add(l.get("kind").asText()));
        return out;
    }
}
