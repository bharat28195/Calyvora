package com.calyvora.people;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The payroll run must agree with the payslips it is a summary of, to the paisa.
 *
 * <p>The run used to call {@code payslip()} once per employee, which meant a dozen database round
 * trips each — about 2,600 queries and seven seconds for two hundred people, thirty for a thousand.
 * Attendance is now fetched for the whole company in one batch and handed to each payslip.
 *
 * <p>That is a change to how money is calculated, or rather to where its inputs come from, so the
 * property worth protecting is not the speed: it is that <b>the totals still equal the sum of the
 * individual payslips</b>. A run that is fast and disagrees with the payslip an employee opens is
 * far worse than one that is slow, because the employee finds it and we do not.
 *
 * <p>Loss of pay is what makes this test bite. It comes from attendance, which is exactly what was
 * batched — so an employee with an absence is the case that would break if the batch path resolved
 * days differently from the single-payslip path.
 */
class PayrollRunBatchingTest extends IntegrationTestBase {

    private static final String PW = "password1234";

    @Test
    void the_run_totals_equal_the_sum_of_the_individual_payslips() throws Exception {
        Session owner = onboardOwner("Payco", "owner@payco.com", PW);
        String employeeId = getJson("/api/v1/people/employees", owner).get(0).get("id").asText();

        mockMvc.perform(post("/api/v1/people/employees/" + employeeId + "/compensation")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("annualAmount", 1_200_000, "currency", "INR",
                                "effectiveDate", java.time.LocalDate.now().withDayOfMonth(1).toString(),
                                "changeType", "HIRE"))))
                .andExpect(status().isOk());

        // An unpaid absence, so loss of pay is non-zero — the figure that is derived from the
        // attendance the run now batches. Without it both paths would trivially agree.
        String yesterday = java.time.LocalDate.now().minusDays(1).toString();
        mockMvc.perform(post("/api/v1/people/attendance/mark")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("employeeId", employeeId, "date", yesterday, "status", "ABSENT"))))
                .andExpect(status().isOk());

        String month = java.time.YearMonth.now().toString();
        JsonNode run = getJson("/api/v1/payroll/run?month=" + month, owner);
        JsonNode slip = getJson("/api/v1/people/employees/" + employeeId + "/payslip?month=" + month, owner);

        assertThat(run.get("rows")).hasSize(1);
        JsonNode row = run.get("rows").get(0);

        assertThat(new BigDecimal(row.get("gross").asText()))
                .as("the run's gross for this person is the payslip's gross")
                .isEqualByComparingTo(new BigDecimal(slip.get("gross").asText()));
        assertThat(new BigDecimal(row.get("net").asText()))
                .as("the run's net is the payslip's net — this is what somebody is actually paid")
                .isEqualByComparingTo(new BigDecimal(slip.get("net").asText()));
        assertThat(row.get("lopDays").asDouble())
                .as("loss of pay comes from the batched attendance and must match the single-payslip path")
                .isEqualTo(slip.get("lopDays").asDouble())
                .isGreaterThan(0);

        // And the run's own totals are consistent with its rows.
        assertThat(new BigDecimal(run.get("totalNet").asText()))
                .isEqualByComparingTo(new BigDecimal(row.get("net").asText()));
        assertThat(new BigDecimal(run.get("totalGross").asText()))
                .isEqualByComparingTo(new BigDecimal(row.get("gross").asText()));
    }
}
