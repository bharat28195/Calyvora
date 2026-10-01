package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Payroll as a background job (backlog 3.1): start, poll, read; tenant-scoped like everything else. */
class PayrollJobIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    private Session demo(String email) throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login(email, PW);
    }

    private JsonNode awaitDone(String jobId, Session session) throws Exception {
        for (int i = 0; i < 100; i++) {
            JsonNode job = getJson("/api/v1/payroll/runs/" + jobId, session);
            if (!"RUNNING".equals(job.get("status").asText())) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("payroll job " + jobId + " still running after 10 s");
    }

    @Test
    @DisplayName("start answers at once; the result arrives by polling and matches the inline run")
    void start_then_poll() throws Exception {
        Session ava = demo("ava.chen@northwind.demo");

        String body = mockMvc.perform(post("/api/v1/payroll/runs?month=2026-09")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(body).get("jobId").asText();

        JsonNode job = awaitDone(jobId, ava);
        assertThat(job.get("status").asText()).isEqualTo("DONE");
        assertThat(job.get("error").isNull()).isTrue();

        // The background thread bound the tenant itself; if it had not, RLS would have shown it no
        // salaries and this would be an empty run, not an error.
        JsonNode inline = getJson("/api/v1/payroll/run?month=2026-09", ava);
        assertThat(job.get("result").get("employees").asInt()).isEqualTo(inline.get("employees").asInt()).isEqualTo(7);
        assertThat(job.get("result").get("totalNet").asDouble()).isEqualTo(inline.get("totalNet").asDouble());
    }

    @Test
    @DisplayName("a job id is worthless to another company")
    void jobs_are_tenant_scoped() throws Exception {
        Session ava = demo("ava.chen@northwind.demo");
        String body = mockMvc.perform(post("/api/v1/payroll/runs?month=2026-09")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String jobId = objectMapper.readTree(body).get("jobId").asText();
        awaitDone(jobId, ava);

        Session other = onboardOwner("Other Co", "owner@otherco.test", "Passw0rd!x");
        mockMvc.perform(get("/api/v1/payroll/runs/" + jobId).header("Authorization", "Bearer " + other.accessToken()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a bad month is refused before any work starts")
    void month_is_validated() throws Exception {
        Session ava = demo("ava.chen@northwind.demo");
        mockMvc.perform(post("/api/v1/payroll/runs?month=September")
                        .header("Authorization", "Bearer " + ava.accessToken()))
                .andExpect(status().isBadRequest());
    }
}
