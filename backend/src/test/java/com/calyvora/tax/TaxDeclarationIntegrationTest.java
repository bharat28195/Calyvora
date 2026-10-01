package com.calyvora.tax;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Declaring tax, and what the screens are told it costs.
 *
 * <p>The arithmetic is proved in {@link IncomeTaxCalculatorTest} against worked examples. What is
 * checked here is everything around it: that a declaration survives a round trip, that the caps are
 * applied on the way out rather than on the way in, that a closed window actually refuses writes,
 * and that one company's declarations are invisible to another.
 */
class TaxDeclarationIntegrationTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    private Session demoOwner() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        return login("ava.chen@northwind.demo", PW);
    }

    private static Map<String, Object> declaration(TaxRegime regime, Map<String, Object> declared) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("regime", regime.name());
        body.put("declared", declared);
        return body;
    }

    @Test
    @DisplayName("an employee with nothing on file gets an empty form, not an error")
    void nothing_declared_is_a_normal_state() throws Exception {
        Session owner = demoOwner();
        JsonNode d = getJson("/api/v1/tax/me/declaration", owner);

        assertThat(d.get("status").asText()).isEqualTo("NOT_STARTED");
        // The statutory default: somebody who never declares anything is taxed under the new regime.
        assertThat(d.get("regime").asText()).isEqualTo("NEW");
        assertThat(d.get("declared").size()).isZero();
        // The form needs the section list to render at all, so it ships with the empty declaration.
        assertThat(d.get("options").size()).isGreaterThan(5);
        assertThat(d.get("financialYear").asText()).matches("\\d{4}-\\d{2}");
    }

    @Test
    @DisplayName("a declaration round-trips, and switching regime keeps what was entered")
    void saving_and_reading_back() throws Exception {
        Session owner = demoOwner();
        Map<String, Object> declared = new LinkedHashMap<>();
        declared.put("SECTION_80C", 150000);
        declared.put("SECTION_80D_SELF", 25000);

        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, declared))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regime").value("OLD"))
                .andExpect(jsonPath("$.declared.SECTION_80C").value(150000));

        // Switching to the new regime must not wipe the entries — the employee may switch back, and
        // retyping a year's investments because they compared the two is its own kind of defect.
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("regime", "NEW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regime").value("NEW"))
                .andExpect(jsonPath("$.declared.SECTION_80C").value(150000));
    }

    @Test
    @DisplayName("clearing a section removes it rather than leaving the old figure behind")
    void a_cleared_claim_is_gone() throws Exception {
        Session owner = demoOwner();
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80C", 150000)))))
                .andExpect(status().isOk());

        // A merge would make this impossible: nobody could ever take a claim back.
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80D_SELF", 20000)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declared.SECTION_80C").doesNotExist())
                .andExpect(jsonPath("$.declared.SECTION_80D_SELF").value(20000));
    }

    @Test
    @DisplayName("an over-cap claim is stored whole and trimmed only when the tax is worked out")
    void the_cap_applies_to_the_tax_not_to_the_form() throws Exception {
        Session owner = demoOwner();
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80C", 500000)))))
                .andExpect(status().isOk())
                // Kept as entered, so the screen can say "you claimed this, that much is allowable".
                .andExpect(jsonPath("$.declared.SECTION_80C").value(500000));

        JsonNode c = getJson("/api/v1/tax/me/computation", owner);
        JsonNode row = null;
        for (JsonNode d : c.get("deductions")) {
            if ("SECTION_80C".equals(d.get("key").asText())) {
                row = d;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.get("declared").asLong()).isEqualTo(500000);
        assertThat(row.get("allowed").asLong())
                .as("80C is capped at 1,50,000 wherever the claim came from")
                .isEqualTo(150000);
    }

    @Test
    @DisplayName("the computation shows its working and adds up")
    void the_working_reconciles() throws Exception {
        Session owner = demoOwner();
        JsonNode c = getJson("/api/v1/tax/me/computation", owner);

        long banded = 0;
        for (JsonNode b : c.get("bands")) {
            banded += b.get("taxable").asLong();
        }
        assertThat(banded)
                .as("the slab rows must account for the whole taxable income")
                .isEqualTo(c.get("taxableIncome").asLong());

        // The screen exists to answer "why am I paying this", so the pieces have to be present.
        assertThat(c.has("rebate")).isTrue();
        assertThat(c.has("cess")).isTrue();
        assertThat(c.get("monthlyTds").asLong() * 12L)
                .isCloseTo(c.get("totalTax").asLong(), org.assertj.core.data.Offset.offset(12L));
        assertThat(c.get("monthsElapsed").asInt()).isBetween(0, 12);
    }

    @Test
    @DisplayName("both regimes are priced, so somebody on the costlier one can see it")
    void the_comparison_prices_both() throws Exception {
        Session owner = demoOwner();
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80C", 150000)))))
                .andExpect(status().isOk());

        JsonNode c = getJson("/api/v1/tax/me/computation", owner);
        JsonNode comparison = c.get("comparison");
        assertThat(comparison.get("oldRegimeTax").isNumber()).isTrue();
        assertThat(comparison.get("newRegimeTax").isNumber()).isTrue();
        assertThat(comparison.get("cheaper").asText()).isIn("OLD", "NEW");
        // Whichever is cheaper, the saving is the gap between them — not a third number.
        long gap = Math.abs(comparison.get("oldRegimeTax").asLong() - comparison.get("newRegimeTax").asLong());
        assertThat(comparison.get("saving").asLong()).isEqualTo(gap);
    }

    @Test
    @DisplayName("a closed window refuses the write, not just the button")
    void closing_the_window_is_enforced_on_the_server() throws Exception {
        Session owner = demoOwner();
        mockMvc.perform(post("/api/v1/tax/window")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("open", false))))
                .andExpect(status().isOk());

        // HR closes declarations before the last payroll of the year so the figures cannot move
        // under a run that has already been filed. Hiding the form would not achieve that.
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80C", 1000)))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/tax/window")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(Map.of("open", true))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80C", 1000)))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a deduction this form does not know about is named, not silently dropped")
    void an_unknown_section_is_refused() throws Exception {
        Session owner = demoOwner();
        // Silently ignoring it would leave somebody with a tax bill they cannot explain.
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80ZZZ", 1000)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("HR sees the company's declarations; a member sees only their own")
    void the_roster_is_hr_only() throws Exception {
        Session owner = demoOwner();
        JsonNode rows = getJson("/api/v1/tax/declarations", owner);
        assertThat(rows.size()).isPositive();
        for (JsonNode row : rows) {
            assertThat(row.has("annualTax")).isTrue();
            assertThat(row.get("status").asText()).isIn("NOT_STARTED", "DRAFT", "SUBMITTED");
        }

        Session member = login("sara.okoro@northwind.demo", PW);
        mockMvc.perform(get("/api/v1/tax/declarations")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isForbidden());
        // But their own declaration is their own business, and needs no role at all.
        mockMvc.perform(get("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + member.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("one company's declarations are invisible to another")
    void tenants_are_separate() throws Exception {
        Session owner = demoOwner();
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.OLD, Map.of("SECTION_80C", 150000)))))
                .andExpect(status().isOk());

        Session other = onboardOwner("Taxco", "admin@taxco.test", "Passw0rd!x");
        JsonNode theirs = getJson("/api/v1/tax/declarations", other);
        for (JsonNode row : theirs) {
            assertThat(row.get("employeeName").asText()).isNotEqualTo("Ava Chen");
        }
        JsonNode theirOwn = getJson("/api/v1/tax/me/declaration", other);
        assertThat(theirOwn.get("declared").size())
                .as("a fresh company starts with nothing declared")
                .isZero();
    }

    @Test
    @DisplayName("submitting marks it declared, and says when")
    void submitting_records_the_moment() throws Exception {
        Session owner = demoOwner();
        mockMvc.perform(put("/api/v1/tax/me/declaration")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(declaration(TaxRegime.NEW, Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(post("/api/v1/tax/me/declaration/submit")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());
    }
}
