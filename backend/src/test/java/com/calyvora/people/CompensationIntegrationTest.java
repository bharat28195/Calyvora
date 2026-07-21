package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Compensation history + payslip (feedback C1–C3): seeded salary with a hike, generated payslip, RBAC.
 */
class CompensationIntegrationTest extends IntegrationTestBase {

    private Session demo(String email) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login(email, "demopass123");
    }

    private String employeeId(Session s, String email) throws Exception {
        for (JsonNode e : getJson("/api/v1/people/employees", s)) {
            if (e.get("email").asText().equals(email)) return e.get("id").asText();
        }
        throw new AssertionError("no employee " + email);
    }

    @Test
    void owner_sees_salary_and_hike_history() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        String id = employeeId(owner, "ava.chen@northwind.demo");
        JsonNode comp = getJson("/api/v1/people/employees/" + id + "/compensation", owner);

        assertThat(comp.get("currentAnnual").asDouble()).isEqualTo(220000.0);
        assertThat(comp.get("history")).hasSize(2);
        // Newest first: the review hike, with a positive hike %.
        assertThat(comp.get("history").get(0).get("changeType").asText()).isEqualTo("HIKE");
        assertThat(comp.get("history").get(0).get("hikePercent").asDouble()).isGreaterThan(0);
        assertThat(comp.get("history").get(1).get("changeType").asText()).isEqualTo("INITIAL");
    }

    @Test
    void payslip_is_generated_from_current_salary() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        String id = employeeId(owner, "ava.chen@northwind.demo");
        JsonNode slip = getJson("/api/v1/people/employees/" + id + "/payslip", owner);

        double gross = slip.get("gross").asDouble();
        assertThat(gross).isCloseTo(220000.0 / 12, org.assertj.core.data.Offset.offset(0.5));
        assertThat(slip.get("earnings")).hasSize(3);
        assertThat(slip.get("net").asDouble()).isLessThan(gross);
    }

    @Test
    void member_cannot_see_compensation() throws Exception {
        Session owner = demo("ava.chen@northwind.demo");
        String id = employeeId(owner, "ava.chen@northwind.demo");
        Session member = login("priya.nair@northwind.demo", "demopass123"); // MEMBER
        mockMvc.perform(get("/api/v1/people/employees/" + id + "/compensation")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
    }
}
