package com.calyvora.document;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Documents module (feedback D2/D3): starter templates, merge-field generation, frozen bodies, isolation. */
class DocumentIntegrationTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    void starter_templates_are_seeded_on_first_open_and_only_once() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);

        JsonNode first = getJson("/api/v1/documents/templates", owner);
        assertThat(first.size()).isEqualTo(5);
        assertThat(first.get(0).get("builtIn").asBoolean()).isTrue();

        // a second open must not duplicate the library
        assertThat(getJson("/api/v1/documents/templates", owner).size()).isEqualTo(5);
    }

    @Test
    void generates_a_letter_from_an_employee_profile() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session owner = login("ava.chen@northwind.demo", "demopass123");

        String templateId = templateOfKind(owner, "JOINING_LETTER");
        String employeeId = anyEmployeeId(owner);

        MvcResult res = mockMvc.perform(post("/api/v1/documents").header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", templateId, "employeeId", employeeId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kind").value("JOINING_LETTER"))
                .andReturn();

        JsonNode doc = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(doc.get("body").asText())
                .contains("Northwind Robotics")          // company merged in
                .doesNotContain("{{");                    // no plumbing left in the letter
        assertThat(doc.get("title").asText()).contains("Joining letter");
        assertThat(doc.get("employeeName").asText()).isNotBlank();
    }

    @Test
    void preview_reports_missing_fields_and_honours_overrides() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String templateId = templateOfKind(owner, "RELIEVING_LETTER");

        // No employee attached: employee fields can't resolve, so preview must say so up front.
        JsonNode preview = postJson("/api/v1/documents/preview", owner,
                Map.of("templateId", templateId));
        assertThat(preview.get("missing").size()).isPositive();
        assertThat(preview.get("missing").toString()).contains("employee.fullName");
        // the unresolved fields are blanked, never left as raw tokens
        assertThat(preview.get("body").asText()).doesNotContain("{{");

        // An override fills the gap without touching the profile.
        Map<String, Object> withOverride = new HashMap<>();
        withOverride.put("templateId", templateId);
        withOverride.put("overrides", Map.of("employee.fullName", "Dana Scully"));
        assertThat(postJson("/api/v1/documents/preview", owner, withOverride).get("body").asText())
                .contains("Dana Scully");
    }

    @Test
    void an_issued_letter_is_frozen_against_later_template_edits() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        String templateId = templateOfKind(owner, "OFFER_LETTER");

        JsonNode issued = postJson("/api/v1/documents", owner, Map.of(
                "templateId", templateId, "title", "Offer — Dana", "overrides", Map.of("employee.firstName", "Dana")));
        String docId = issued.get("id").asText();

        mockMvc.perform(patch("/api/v1/documents/templates/" + templateId).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("body", "Completely rewritten template."))))
                .andExpect(status().isOk());

        assertThat(getJson("/api/v1/documents/" + docId, owner).get("body").asText())
                .contains("Dana")
                .doesNotContain("Completely rewritten");
    }

    @Test
    void documents_are_admin_only_and_tenant_isolated() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session member = login("priya.nair@northwind.demo", "demopass123"); // MEMBER
        mockMvc.perform(get("/api/v1/documents/templates").header("Authorization", bearer(member)))
                .andExpect(status().isForbidden());

        Session other = onboardOwner("Other Co", "owner@other.com", PW);
        assertThat(getJson("/api/v1/documents", other).size()).isZero();  // none of Northwind's letters
    }

    @Test
    void field_catalogue_is_available_to_template_authors() throws Exception {
        Session owner = onboardOwner("Acme", "owner@acme.com", PW);
        JsonNode fields = getJson("/api/v1/documents/fields", owner);
        assertThat(fields.size()).isGreaterThan(10);
        assertThat(fields.get(0).get("key").asText()).isEqualTo("employee.fullName");
    }

    // ---- helpers ----

    private String bearer(Session s) {
        return "Bearer " + s.accessToken();
    }

    private JsonNode postJson(String path, Session s, Map<String, ?> body) throws Exception {
        MvcResult res = mockMvc.perform(post(path).header("Authorization", bearer(s))
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().is2xxSuccessful()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String templateOfKind(Session owner, String kind) throws Exception {
        for (JsonNode t : getJson("/api/v1/documents/templates", owner)) {
            if (kind.equals(t.get("kind").asText())) {
                return t.get("id").asText();
            }
        }
        throw new AssertionError("no starter template of kind " + kind);
    }

    private String anyEmployeeId(Session owner) throws Exception {
        return getJson("/api/v1/people/employees", owner).get(0).get("id").asText();
    }
}
