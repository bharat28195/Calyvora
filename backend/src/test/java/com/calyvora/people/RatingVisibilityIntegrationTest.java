package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The employee directory is readable by every member of a company — that's the point of a directory.
 * The performance rating rode along in the same payload, which meant any employee could read every
 * colleague's score. Salary was already protected behind a self-only endpoint; this closes the same
 * gap for ratings, and these tests keep it closed.
 */
class RatingVisibilityIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    @Test
    void an_employee_cannot_see_a_colleagues_rating() throws Exception {
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);   // a plain MEMBER

        JsonNode directory = getJson("/api/v1/people/employees", sara);

        JsonNode ava = byEmail(directory, "ava.chen@northwind.demo");
        assertThat(ava.get("rating").isNull())
                .as("a colleague's performance rating must not be readable from the directory")
                .isTrue();
    }

    @Test
    void an_employee_can_still_see_their_own_rating() throws Exception {
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        JsonNode me = byEmail(getJson("/api/v1/people/employees", sara), "sara.okoro@northwind.demo");

        // Redaction must not hide a person's own score from them — that would break "my performance".
        assertThat(me.get("rating").isNull()).isFalse();
    }

    @Test
    void hr_and_admins_still_see_every_rating() throws Exception {
        seed();
        Session ava = login("ava.chen@northwind.demo", PW);      // ADMIN

        JsonNode directory = getJson("/api/v1/people/employees", ava);

        assertThat(byEmail(directory, "sara.okoro@northwind.demo").get("rating").isNull())
                .as("HR and leadership run performance; redacting for them would break the feature")
                .isFalse();
    }

    @Test
    void the_paged_directory_redacts_too() throws Exception {
        // Same data, second endpoint — a fix applied to only one of them is not a fix.
        seed();
        Session sara = login("sara.okoro@northwind.demo", PW);

        JsonNode page = getJson("/api/v1/people/employees/page?size=50&page=0", sara);

        JsonNode ava = byEmail(page.get("content"), "ava.chen@northwind.demo");
        assertThat(ava.get("rating").isNull()).isTrue();
    }

    private void seed() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
    }

    private static JsonNode byEmail(JsonNode list, String email) {
        for (JsonNode e : list) {
            if (e.get("email").asText().equals(email)) {
                return e;
            }
        }
        throw new AssertionError("employee not found: " + email);
    }
}
