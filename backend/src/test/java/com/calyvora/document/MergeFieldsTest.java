package com.calyvora.document;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The merge engine (feedback D2) — pure, so it's tested without booting the app. */
class MergeFieldsTest {

    @Test
    void substitutes_known_fields_and_tolerates_inner_whitespace() {
        String out = MergeFields.render("Dear {{employee.firstName}}, welcome to {{ company.name }}.",
                Map.of("employee.firstName", "Leo", "company.name", "Northwind"));
        assertThat(out).isEqualTo("Dear Leo, welcome to Northwind.");
    }

    @Test
    void unknown_or_empty_fields_never_leak_the_raw_token() {
        String out = MergeFields.render("Last day: {{employee.endDate}} / {{nope.field}}",
                Map.of("employee.endDate", " "));
        assertThat(out).isEqualTo("Last day: — / —");
        assertThat(out).doesNotContain("{{");
    }

    @Test
    void replacement_values_with_dollars_or_backslashes_are_literal() {
        String out = MergeFields.render("Pay: {{salary.annual}}", Map.of("salary.annual", "$1,20,000\\yr"));
        assertThat(out).isEqualTo("Pay: $1,20,000\\yr");
    }

    @Test
    void lists_distinct_placeholders_in_first_seen_order() {
        assertThat(MergeFields.placeholdersIn("{{b}} {{a}} {{b}}")).containsExactly("b", "a");
        assertThat(MergeFields.placeholdersIn(null)).isEmpty();
    }

    @Test
    void formats_dates_the_way_a_letter_reads() {
        assertThat(MergeFields.date(LocalDate.of(2026, 3, 4))).isEqualTo("4 March 2026");
        assertThat(MergeFields.date(null)).isNull();
    }

    @Test
    void computes_human_tenure() {
        assertThat(MergeFields.tenure(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 4, 1)))
                .isEqualTo("2 years 3 months");
        assertThat(MergeFields.tenure(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 6))).isEqualTo("5 days");
        assertThat(MergeFields.tenure(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1))).isEqualTo("1 month");
        assertThat(MergeFields.tenure(null, LocalDate.of(2026, 1, 1))).isNull();
        assertThat(MergeFields.tenure(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 1, 1))).isNull();
    }
}
