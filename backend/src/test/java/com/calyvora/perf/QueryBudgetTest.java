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

    /**
     * The team summary must cost rows proportional to the team, not to the company.
     *
     * <p>This is a different defect from the two above and the statement counter cannot see it: the
     * screen issued about six queries — a perfectly respectable number — and each one read the whole
     * company. A lead of a hundred people pulled every employee three times over, plus the company's
     * entire month of attendance, its whole leave history and all of its expense claims, then threw
     * nine rows in ten away in memory. On the thousand-person tenant that was 4.3 seconds.
     *
     * <p>So the budget here is <b>entities loaded</b>, which is the quantity that was actually wrong.
     */
    @Test
    void the_team_summary_does_not_read_the_whole_company() throws Exception {
        Session head = seedScale().head();

        long before = stats().getEntityLoadCount();
        JsonNode team = getJson("/api/v1/team?month=" + java.time.YearMonth.now(), head);
        long loaded = stats().getEntityLoadCount() - before;

        int roster = team.get("totalCount").asInt();
        assertThat(roster)
                .as("this lead must actually have a team, or the budget below proves nothing")
                .isGreaterThan(0);
        // And the team must be a proper fraction of the company, or "scoped to the roster" and
        // "scoped to the company" are the same number and the test cannot tell them apart.
        assertThat(roster)
                .as("the head's downline must be smaller than the company for this to mean anything")
                .isLessThan(HEADCOUNT);
        assertThat(team.get("members").size()).isEqualTo(roster);

        // The roster's employees, their managers, a month of their attendance, their open leave and
        // expenses. Measured at 187 for a team of 58 on a day whose two-day attendance window fell on
        // a weekend, and the seeder skips those; on a midweek run the same call carries two days of
        // real attendance for each of them on top. The same call before it was scoped cost upwards of
        // 700, and more midweek for the same reason, so the budget sits clearly between the two
        // rather than snugly above the good number.
        long budget = roster * 8L + 80;
        assertThat(loaded)
                .as("the summary for a team of %d loaded %d entities against a budget of %d — that is "
                        + "company-sized work on a team-sized screen", roster, loaded, budget)
                .isLessThan(budget);
    }

    /**
     * The team review list must not cost a handful of queries per review.
     *
     * <p>Rendering one review fetched the employee, their manager, the cycle, their goals and their
     * salary — five round trips — and the list did that for every review below the caller. This one is
     * a statement budget rather than an entity budget because the defect was round trips: the rows
     * were small, there were simply hundreds of separate visits to fetch them.
     */
    @Test
    void the_team_review_list_does_not_cost_a_query_per_review() throws Exception {
        Session head = seedScale().head();

        long before = stats().getPrepareStatementCount();
        JsonNode reviews = getJson("/api/v1/team/performance", head);
        long statements = stats().getPrepareStatementCount() - before;

        assertThat(reviews.size())
                .as("the seeded tenant must actually have reviews below this lead, or the budget "
                        + "below is measuring an empty list")
                .isGreaterThan(10);

        assertThat(statements)
                .as("%d reviews took %d SQL statements — that is per-review work", reviews.size(), statements)
                .isLessThan(40);
    }

    // --- helpers ---------------------------------------------------------------

    private Session seedScaleTenant() throws Exception {
        return seedScale().admin();
    }

    /** The seeded tenant's two useful logins: the admin who sees everyone, and a lead who does not. */
    private record ScaleTenant(Session admin, Session head) {
    }

    private ScaleTenant seedScale() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/dev/seed-scale?employees=" + HEADCOUNT + "&attendanceDays=2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seeded = objectMapper.readTree(r.getResponse().getContentAsString());
        String password = seeded.get("password").asText();
        return new ScaleTenant(login(seeded.get("adminEmail").asText(), password),
                login(seeded.get("headEmail").asText(), password));
    }

    /** Payroll is run for a month that has finished, which is how anyone actually runs it. */
    private static String lastMonth() {
        return java.time.YearMonth.now().minusMonths(1).toString();
    }
}
