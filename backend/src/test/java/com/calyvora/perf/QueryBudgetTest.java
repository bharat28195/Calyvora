package com.calyvora.perf;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.persistence.EntityManagerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a screen costs the database, asserted as a number.
 *
 * <p>The suite proves the product is <em>correct</em> and says nothing about what it costs. That gap
 * is not theoretical: five separate N+1 defects have shipped through a green suite, and one version
 * of the payroll work made the run four times slower while another made it time out entirely. Every
 * test passed through all of them.
 *
 * <p>The budgets below are deliberately generous. The point is not to police a handful of statements
 * — a new feature is allowed to cost a query — it is to catch the shape of defect that has actually
 * happened here every time: <b>work proportional to headcount</b>. At 120 people an N+1 of five
 * queries each is six hundred statements, so a budget of a few hundred still fails by an order of
 * magnitude while leaving room for ordinary change.
 *
 * <p>Statistics are switched on for this class only. They are cheap but not free, and every other
 * test has no use for them.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryBudgetTest extends IntegrationTestBase {

    /** Enough people that per-row work is unmistakable, few enough that seeding stays quick. */
    private static final int HEADCOUNT = 120;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics stats() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void a_payroll_run_does_not_cost_a_query_per_employee() throws Exception {
        Session admin = seedScaleTenant();

        long before = stats().getPrepareStatementCount();
        JsonNode run = getJson("/api/v1/payroll/run?month=" + lastMonth(), admin);
        long statements = stats().getPrepareStatementCount() - before;
        // A timing or a count means nothing if the run did no work. This tenant had no compensation
        // at all until the seed was fixed, so a payroll run over it produced zero payslips very
        // quickly — which is exactly what an unchecked budget would have called a pass.
        assertThat(run.get("employees").asInt())
                .as("the run must actually pay people, or this budget proves nothing")
                .isEqualTo(HEADCOUNT);

        assertThat(statements)
                .as("payroll for %d people took %d SQL statements — that is per-employee work, "
                        + "which is the defect this test exists to catch", HEADCOUNT, statements)
                .isLessThan(200);
    }

    @Test
    void the_attendance_day_sheet_does_not_cost_a_query_per_employee() throws Exception {
        Session admin = seedScaleTenant();

        long before = stats().getPrepareStatementCount();
        JsonNode day = getJson("/api/v1/people/attendance/day?date=" + java.time.LocalDate.now(), admin);
        long statements = stats().getPrepareStatementCount() - before;

        assertThat(day.get("entries").size())
                .as("the sheet must actually list people")
                .isEqualTo(HEADCOUNT);

        // This screen carried four separate per-row lookups at once: the roster loaded three times,
        // the company's whole leave history read to answer about one day, and a department name
        // fetched per person. It cost sixteen seconds at a thousand people.
        assertThat(statements)
                .as("the day sheet for %d people took %d SQL statements", HEADCOUNT, statements)
                .isLessThan(100);
    }

    // --- helpers ---------------------------------------------------------------

    private Session seedScaleTenant() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/dev/seed-scale?employees=" + HEADCOUNT + "&attendanceDays=2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seeded = objectMapper.readTree(r.getResponse().getContentAsString());
        return login(seeded.get("adminEmail").asText(), seeded.get("password").asText());
    }

    /** Payroll is run for a month that has finished, which is how anyone actually runs it. */
    private static String lastMonth() {
        return java.time.YearMonth.now().minusMonths(1).toString();
    }
}
