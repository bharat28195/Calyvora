package com.calyvora.payroll;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bank file, checked line by line.
 *
 * <p>This file moves money out of a company's account. Most of these tests are about what it refuses
 * to do rather than what it produces — a file that is quietly one row short is how somebody goes
 * unpaid for a month and nobody notices until they say so.
 */
class BankFileBuilderTest {

    private BankFileBuilder.Payee payee(String name, String account, String ifsc, int net) {
        return new BankFileBuilder.Payee("emp-" + name, name, account, ifsc, "HDFC Bank", BigDecimal.valueOf(net));
    }

    private BankFileBuilder.Result build(List<BankFileBuilder.Payee> payees) {
        return BankFileBuilder.build(payees, BankFileFormat.GENERIC, "2026-09");
    }

    @Test
    void a_clean_payroll_produces_a_line_per_person_and_the_right_total() {
        BankFileBuilder.Result r = build(List.of(
                payee("Ava Chen", "50100424268412", "HDFC0003939", 84_600),
                payee("Tom Becker", "918010049211334", "UTIB0001234", 61_250)));

        assertThat(r.included()).hasSize(2);
        assertThat(r.excluded()).isEmpty();
        assertThat(r.total()).isEqualByComparingTo(BigDecimal.valueOf(145_850));
        assertThat(r.csv().lines().count()).isEqualTo(3);   // header + two rows
        assertThat(r.csv()).contains("50100424268412").contains("SALARY 2026-09");
    }

    @Test
    void somebody_with_no_account_number_is_reported_not_skipped() {
        // The failure this prevents: a file one row shorter than the payroll, and nobody told.
        BankFileBuilder.Result r = build(List.of(
                payee("Ava Chen", "50100424268412", "HDFC0003939", 84_600),
                payee("Priya Nair", null, "HDFC0003939", 45_000)));

        assertThat(r.included()).hasSize(1);
        assertThat(r.excluded()).hasSize(1);
        assertThat(r.excluded().get(0).name()).isEqualTo("Priya Nair");
        assertThat(r.excluded().get(0).reason()).contains("account number");
        assertThat(r.total()).isEqualByComparingTo(BigDecimal.valueOf(84_600));
    }

    @Test
    void a_malformed_ifsc_is_caught_here_rather_than_by_the_bank() {
        // A bad IFSC is rejected for the WHOLE batch at upload, not just that row — so catching it
        // before the file exists is the difference between fixing one record and re-running payday.
        BankFileBuilder.Result r = build(List.of(payee("Leo Martins", "002401527788", "ICIC24", 52_000)));

        assertThat(r.included()).isEmpty();
        assertThat(r.excluded().get(0).reason()).contains("not valid");
    }

    @Test
    void a_valid_ifsc_is_accepted_in_its_exact_shape() {
        // Four letters, a zero, six alphanumerics.
        assertThat(build(List.of(payee("A", "1", "SBIN0011513", 100))).included()).hasSize(1);
        assertThat(build(List.of(payee("A", "1", "SBIN1011513", 100))).excluded()).hasSize(1);   // no zero
        assertThat(build(List.of(payee("A", "1", "SBI0011513", 100))).excluded()).hasSize(1);    // 3 letters
    }

    @Test
    void nobody_is_paid_zero() {
        // Legitimate — a month of unpaid leave — but a zero-value transfer is rejected by the bank
        // and would fail the batch for everyone else.
        BankFileBuilder.Result r = build(List.of(payee("Sara Okoro", "38240015566", "SBIN0011513", 0)));

        assertThat(r.included()).isEmpty();
        assertThat(r.excluded().get(0).reason()).contains("Nothing payable");
    }

    @Test
    void a_comma_in_a_name_cannot_shift_the_columns() {
        // THE ERROR THIS PINS. Unescaped, "Rao, Renu" pushes the account number into the IFSC column
        // and the amount into the mode column. The bank either rejects the batch or pays a number
        // that happened to parse.
        BankFileBuilder.Result r = build(List.of(payee("Rao, Renu", "50100424268412", "HDFC0003939", 90_000)));

        assertThat(r.csv()).contains("\"Rao, Renu\"");
        String row = r.csv().lines().skip(1).findFirst().orElseThrow();
        assertThat(row.split("\",")[1]).startsWith("HDFC Bank,50100424268412,HDFC0003939,90000.00");
    }

    @Test
    void an_empty_payroll_produces_no_file_at_all() {
        // Not a header-only file: something that looks downloadable but pays nobody is worse than
        // nothing, because it can be uploaded.
        BankFileBuilder.Result r = build(List.of(payee("Sara", "1", "SBIN0011513", 0)));

        assertThat(r.csv()).isEmpty();
        assertThat(r.total()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void each_bank_gets_its_own_header_row() {
        List<BankFileBuilder.Payee> one = List.of(payee("Ava Chen", "50100424268412", "HDFC0003939", 84_600));

        assertThat(BankFileBuilder.build(one, BankFileFormat.HDFC, "2026-09").csv())
                .startsWith("Beneficiary Name,Beneficiary Account Number,IFSC,Amount,Transaction Type,Narration");
        assertThat(BankFileBuilder.build(one, BankFileFormat.ICICI, "2026-09").csv())
                .startsWith("Payee Name,Account Number,IFSC Code,Amount,Payment Mode,Remarks");
        assertThat(BankFileBuilder.build(one, BankFileFormat.AXIS, "2026-09").csv())
                .startsWith("Beneficiary Name,Account No,IFSC,Amount,Mode,Narration,Value Date");
    }

    @Test
    void the_total_is_what_is_in_the_file_not_what_payroll_said() {
        // The number HR checks against their bank balance must match the file, not the payroll run —
        // those differ exactly when somebody has been excluded, which is when it matters most.
        BankFileBuilder.Result r = build(List.of(
                payee("Ava Chen", "50100424268412", "HDFC0003939", 84_600),
                payee("No Bank", null, null, 45_000)));

        assertThat(r.total()).isEqualByComparingTo(BigDecimal.valueOf(84_600));
    }

    @Test
    void an_unsupported_bank_is_refused_by_name() {
        assertThat(BankFileFormat.parse("hdfc")).isEqualTo(BankFileFormat.HDFC);
        assertThat(BankFileFormat.parse(null)).isEqualTo(BankFileFormat.GENERIC);
        assertThat(BankFileFormat.parse("")).isEqualTo(BankFileFormat.GENERIC);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> BankFileFormat.parse("KOTAK"))
                .hasMessageContaining("GENERIC, HDFC, ICICI, AXIS");
    }
}
