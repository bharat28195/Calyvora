package com.calyvora.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provident Fund arithmetic, checked against wages and expected splits.
 *
 * <p>These are the numbers that go on a payslip, come out of somebody's salary, and are filed with
 * the EPFO. Every case here is a wage and an answer that can be checked by hand against the EPF &amp;
 * MP Act, which is the only way to have any confidence in a compliance calculation.
 */
class PfCalculatorTest {

    private static final UUID COMPANY = UUID.randomUUID();

    private PfSettings settings() {
        return PfSettings.defaults(COMPANY);
    }

    private static BigDecimal rupees(int amount) {
        return BigDecimal.valueOf(amount);
    }

    @Test
    void a_wage_below_the_ceiling_splits_the_standard_way() {
        // Basic 10,000. Employee 12% = 1,200. Employer 12% = 1,200, of which EPS 8.33% = 833 and the
        // rest to EPF = 367. Admin 0.5% = 50, EDLI 0.5% = 50.
        PfCalculator.Result r = PfCalculator.compute(rupees(10_000), settings());

        assertThat(r.pfWages()).isEqualByComparingTo(rupees(10_000));
        assertThat(r.employee()).isEqualByComparingTo(rupees(1_200));
        assertThat(r.employerEps()).isEqualByComparingTo(rupees(833));
        assertThat(r.employerEpf()).isEqualByComparingTo(rupees(367));
        assertThat(r.adminCharges()).isEqualByComparingTo(rupees(50));
        assertThat(r.edli()).isEqualByComparingTo(rupees(50));
        assertThat(r.employerTotal()).isEqualByComparingTo(rupees(1_300));
    }

    @Test
    void the_two_employer_halves_always_add_back_to_the_employer_contribution() {
        // The reason EPF is derived by subtraction rather than as its own percentage: 8.33% of an odd
        // wage rounds, and computing the remainder separately would leave the halves disagreeing with
        // the total by a rupee — in a return that lists both.
        for (int basic : new int[] {7_777, 9_999, 12_345, 14_999, 3_333}) {
            PfCalculator.Result r = PfCalculator.compute(rupees(basic), settings());
            assertThat(r.employerEps().add(r.employerEpf()))
                    .as("EPS + EPF for a basic of %d", basic)
                    .isEqualByComparingTo(r.employee());   // employer rate == employee rate by default
        }
    }

    @Test
    void contributions_are_capped_at_the_ceiling_by_default() {
        // Basic 50,000, ceiling 15,000: everything is computed on 15,000, not 50,000.
        PfCalculator.Result r = PfCalculator.compute(rupees(50_000), settings());

        assertThat(r.pfWages()).isEqualByComparingTo(rupees(15_000));
        assertThat(r.employee()).isEqualByComparingTo(rupees(1_800));
        assertThat(r.employerEps()).isEqualByComparingTo(rupees(1_250));   // 8.33% of 15,000
        assertThat(r.employerEpf()).isEqualByComparingTo(rupees(550));
    }

    @Test
    void opting_out_of_the_ceiling_raises_pf_but_not_eps() {
        // THE ERROR THIS PINS. A company may lawfully contribute on the real wage, but EPS stays
        // capped at the ceiling regardless. Computing 8.33% of 50,000 would put 4,165 into the pension
        // share instead of 1,250 — the employer total is unchanged, so nothing looks wrong until the
        // EPFO rejects the return.
        PfSettings uncapped = settings();
        uncapped.setRestrictToCeiling(false);

        PfCalculator.Result r = PfCalculator.compute(rupees(50_000), uncapped);

        assertThat(r.pfWages()).isEqualByComparingTo(rupees(50_000));
        assertThat(r.employee()).isEqualByComparingTo(rupees(6_000));
        assertThat(r.employerEps())
                .as("EPS stays capped at 8.33%% of the ceiling")
                .isEqualByComparingTo(rupees(1_250));
        assertThat(r.employerEpf())
                .as("the whole balance of the employer's 12%% goes to EPF")
                .isEqualByComparingTo(rupees(4_750));
        assertThat(r.employerEps().add(r.employerEpf())).isEqualByComparingTo(rupees(6_000));
    }

    @Test
    void edli_is_computed_on_the_capped_wage_even_when_pf_is_not() {
        // EDLI follows the pension ceiling, not the PF wage. Same trap as EPS.
        PfSettings uncapped = settings();
        uncapped.setRestrictToCeiling(false);

        PfCalculator.Result r = PfCalculator.compute(rupees(50_000), uncapped);

        assertThat(r.edli()).isEqualByComparingTo(rupees(75));   // 0.5% of 15,000
    }

    @Test
    void a_wage_exactly_on_the_ceiling_is_not_a_special_case() {
        PfCalculator.Result r = PfCalculator.compute(rupees(15_000), settings());

        assertThat(r.pfWages()).isEqualByComparingTo(rupees(15_000));
        assertThat(r.employee()).isEqualByComparingTo(rupees(1_800));
        assertThat(r.employerEps()).isEqualByComparingTo(rupees(1_250));
    }

    @Test
    void rounding_is_to_whole_rupees() {
        // 8.33% of 7,777 is 647.82... The ECR file and every Indian payslip carry whole rupees.
        PfCalculator.Result r = PfCalculator.compute(rupees(7_777), settings());

        assertThat(r.employerEps()).isEqualByComparingTo(rupees(648));
        assertThat(r.employee()).isEqualByComparingTo(rupees(933));   // 12% of 7,777 = 933.24
        assertThat(r.employee().scale()).isZero();
    }

    @Test
    void a_changed_ceiling_is_a_setting_not_a_release() {
        // The ceiling has moved before and is expected to move again. Proving it is honoured from the
        // row is what makes that an UPDATE rather than a redeploy.
        PfSettings raised = settings();
        raised.setWageCeiling(rupees(21_000));

        PfCalculator.Result r = PfCalculator.compute(rupees(50_000), raised);

        assertThat(r.pfWages()).isEqualByComparingTo(rupees(21_000));
        assertThat(r.employee()).isEqualByComparingTo(rupees(2_520));
        assertThat(r.employerEps()).isEqualByComparingTo(rupees(1_749));   // 8.33% of 21,000
    }

    @Test
    void nothing_is_computed_for_a_zero_or_missing_wage() {
        assertThat(PfCalculator.compute(BigDecimal.ZERO, settings())).isEqualTo(PfCalculator.Result.NONE);
        assertThat(PfCalculator.compute(null, settings())).isEqualTo(PfCalculator.Result.NONE);
        assertThat(PfCalculator.compute(rupees(-500), settings())).isEqualTo(PfCalculator.Result.NONE);
    }

    @Test
    void an_eps_rate_above_the_employer_rate_cannot_produce_a_credit() {
        // The database CHECK prevents such a row; this proves the calculator does not go negative if
        // one ever arrives by another path.
        PfSettings broken = settings();
        broken.setEpsRate(BigDecimal.valueOf(20));

        PfCalculator.Result r = PfCalculator.compute(rupees(10_000), broken);

        assertThat(r.employerEpf()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
