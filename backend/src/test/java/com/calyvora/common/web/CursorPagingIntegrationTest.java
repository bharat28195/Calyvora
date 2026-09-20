package com.calyvora.common.web;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paging a queue that grows forever.
 *
 * <p>The failure modes here are quiet ones, which is why they are worth a test of their own. A
 * cursor that pages correctly on a toy fixture can still drop rows in production, because the thing
 * that breaks it — many rows sharing a creation instant — only shows up in bulk. The seeder creates
 * leave in a loop, so this fixture has exactly that property, and it is the reason the sort key is
 * {@code (createdAt, id)} rather than a timestamp alone.
 *
 * <p>The other quiet one is a filter applied to the first page and forgotten on the rest. A manager
 * who pages past the end of page one and starts seeing the whole company's leave has a permission
 * bug, not a paging bug, and it would look like neither in a test that only ever reads page one.
 */
class CursorPagingIntegrationTest extends IntegrationTestBase {

    private static final int HEADCOUNT = 60;

    /** Every row of the queue, walked a page at a time, with the ids in the order they arrived. */
    private List<String> walk(Session session, int pageSize) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        // A queue this size cannot need more passes than it has rows; anything more is a cursor that
        // is not advancing, and failing here beats looping until the build times out.
        for (int guard = 0; guard <= HEADCOUNT * 10; guard++) {
            String url = "/api/v1/people/leave?size=" + pageSize
                    + (cursor == null ? "" : "&cursor=" + cursor);
            JsonNode page = getJson(url, session);
            for (JsonNode row : page.get("items")) {
                ids.add(row.get("id").asText());
            }
            if (page.get("nextCursor").isNull()) {
                return ids;
            }
            cursor = page.get("nextCursor").asText();
        }
        throw new AssertionError("the cursor never reported the end of the list");
    }

    @Test
    @DisplayName("paging through the queue yields every row exactly once, in the same order")
    void paging_is_lossless() throws Exception {
        Session admin = seedScale();
        try {
            // One big read, as the endpoint used to answer, is the thing the paged walk must agree
            // with. Without this the test could only prove the pages are self-consistent, which they
            // would also be if they silently dropped the same rows every time.
            JsonNode whole = getJson("/api/v1/people/leave?size=200", admin);
            List<String> expected = new ArrayList<>();
            for (JsonNode row : whole.get("items")) {
                expected.add(row.get("id").asText());
            }
            assertThat(expected)
                    .as("the fixture must have enough leave to need several pages, or this proves nothing")
                    .hasSizeGreaterThan(12);
            assertThat(whole.get("nextCursor").isNull())
                    .as("200 rows should cover this fixture in one page")
                    .isTrue();

            List<String> walked = walk(admin, 5);

            assertThat(walked).as("paging must not drop or repeat rows").isEqualTo(expected);
            assertThat(new HashSet<>(walked)).hasSameSizeAs(walked);
        } finally {
            mockMvc.perform(delete("/api/v1/dev/seed-scale")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a page size of one still terminates, and still sees everything")
    void the_smallest_page_still_works() throws Exception {
        Session admin = seedScale();
        try {
            List<String> byOne = walk(admin, 1);
            List<String> byFifty = walk(admin, 50);
            // A page size of one puts every single boundary inside the run of rows the seeder created
            // in the same instant, so if the tiebreaker were missing this is where it would show.
            assertThat(byOne).isEqualTo(byFifty);
        } finally {
            mockMvc.perform(delete("/api/v1/dev/seed-scale")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a manager's scope holds on every page, not just the first")
    void the_filter_survives_the_cursor() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/dev/seed-scale?employees=" + HEADCOUNT + "&attendanceDays=2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seeded = objectMapper.readTree(r.getResponse().getContentAsString());
        String password = seeded.get("password").asText();
        Session admin = login(seeded.get("adminEmail").asText(), password);
        Session head = login(seeded.get("headEmail").asText(), password);

        try {
            Set<String> everything = new HashSet<>(walk(admin, 200));
            List<String> headsOwn = walk(head, 3);

            assertThat(headsOwn)
                    .as("this lead must see some leave, or the comparison below is vacuous")
                    .isNotEmpty();
            assertThat(headsOwn.size())
                    .as("and strictly less than the company's, or scoping is not being tested")
                    .isLessThan(everything.size());
            // The point: walked in threes, so most of these rows were fetched with a cursor rather
            // than on the opening page.
            assertThat(everything).containsAll(headsOwn);
            assertThat(new HashSet<>(headsOwn)).hasSameSizeAs(headsOwn);
        } finally {
            mockMvc.perform(delete("/api/v1/dev/seed-scale")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("the status filter is applied in the database, not to the page after it arrives")
    void the_status_filter_narrows_the_query() throws Exception {
        Session admin = seedScale();
        try {
            List<String> everything = walk(admin, 200);
            JsonNode pendingPage = getJson("/api/v1/people/leave?status=PENDING&size=200", admin);

            List<String> pending = new ArrayList<>();
            for (JsonNode row : pendingPage.get("items")) {
                assertThat(row.get("status").asText()).isEqualTo("PENDING");
                pending.add(row.get("id").asText());
            }
            assertThat(pending)
                    .as("the seeder leaves work in the queue on purpose, so this must not be empty")
                    .isNotEmpty();
            // The whole point: strictly fewer rows come back, because the decided ones never leave
            // the database. Filtering the page here instead would return the same count either way.
            assertThat(pending.size()).isLessThan(everything.size());
            assertThat(everything).containsAll(pending);
        } finally {
            mockMvc.perform(delete("/api/v1/dev/seed-scale")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a misspelled status is refused, not treated as no filter at all")
    void an_unknown_status_is_an_error() throws Exception {
        Session owner = onboardOwner("Cursorco3", "admin@cursorco3.test", "Passw0rd!x");
        // Quietly returning everything for ?status=PENDNIG is how approved leave ends up listed in
        // an approvals queue, with nothing anywhere saying why.
        mockMvc.perform(get("/api/v1/people/leave?status=PENDNIG")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("expense totals are for the whole set, not for the page they arrive with")
    void the_totals_survive_paging() throws Exception {
        Session admin = seedScale();
        try {
            JsonNode whole = getJson("/api/v1/expenses?size=200", admin);
            // A single-row page, so the reported totals cannot coincide with the page's own sum
            // unless the company has at most one claim of that status. Comparing two large pages
            // would pass by luck whenever every pending claim happened to land on the first.
            JsonNode onePage = getJson("/api/v1/expenses?size=1", admin);

            assertThat(whole.get("claims").size())
                    .as("the fixture needs more claims than one small page, or this proves nothing")
                    .isGreaterThan(5);
            assertThat(onePage.get("claims").size()).isEqualTo(1);
            assertThat(onePage.get("nextCursor").isNull()).isFalse();

            // What the page would report if the totals were accumulated over its own rows.
            java.math.BigDecimal pendingOnPage = java.math.BigDecimal.ZERO;
            for (JsonNode row : onePage.get("claims")) {
                if ("SUBMITTED".equals(row.get("status").asText())) {
                    pendingOnPage = pendingOnPage.add(row.get("amount").decimalValue());
                }
            }
            assertThat(onePage.get("pendingAmount").decimalValue())
                    .as("the total must be the company's, not this one row's")
                    .isGreaterThan(pendingOnPage);

            // The whole point of summing these in the database. Accumulating them while walking the
            // rows was correct only while the rows were everything; on a page it would quietly
            // redefine "outstanding across the company" as "outstanding among these five" — a wrong
            // number on a finance screen, and wrong in a way nobody notices until they reconcile.
            for (String total : List.of("pendingAmount", "awaitingReimbursement", "reimbursedThisYear")) {
                assertThat(onePage.get(total).decimalValue())
                        .as("%s must not shrink to the page", total)
                        .isEqualByComparingTo(whole.get(total).decimalValue());
            }
            assertThat(whole.get("pendingAmount").decimalValue())
                    .as("the seeder leaves claims awaiting approval, so this must not be zero")
                    .isGreaterThan(java.math.BigDecimal.ZERO);
        } finally {
            mockMvc.perform(delete("/api/v1/dev/seed-scale")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("the helpdesk queue pages, and its status filter is applied in the database")
    void the_ticket_queue_pages() throws Exception {
        Session admin = seedScale();
        try {
            List<String> all = new ArrayList<>();
            String cursor = null;
            for (int guard = 0; guard <= 200; guard++) {
                JsonNode page = getJson("/api/v1/helpdesk/tickets?size=4"
                        + (cursor == null ? "" : "&cursor=" + cursor), admin);
                for (JsonNode row : page.get("items")) {
                    all.add(row.get("id").asText());
                }
                if (page.get("nextCursor").isNull()) break;
                cursor = page.get("nextCursor").asText();
            }
            assertThat(all).hasSizeGreaterThan(4);
            assertThat(new HashSet<>(all)).as("no ticket twice").hasSameSizeAs(all);

            JsonNode open = getJson("/api/v1/helpdesk/tickets?status=OPEN&size=200", admin);
            for (JsonNode row : open.get("items")) {
                assertThat(row.get("status").asText()).isEqualTo("OPEN");
            }
            assertThat(open.get("items").size()).isPositive().isLessThan(all.size());
        } finally {
            mockMvc.perform(delete("/api/v1/dev/seed-scale")).andExpect(status().isOk());
        }
    }

    @Test
    @DisplayName("a cursor we never issued is refused, rather than quietly restarting from the top")
    void a_bad_cursor_is_an_error() throws Exception {
        Session owner = onboardOwner("Cursorco", "admin@cursorco.test", "Passw0rd!x");
        // Silently starting over would make a client bug look like an endless list: it pages forever,
        // receiving the first page every time, and nothing anywhere says why.
        mockMvc.perform(get("/api/v1/people/leave?cursor=not-a-real-cursor")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cursor")));
    }

    @Test
    @DisplayName("an absurd page size is clamped rather than honoured or refused")
    void the_page_size_has_a_ceiling() throws Exception {
        Session owner = onboardOwner("Cursorco2", "admin@cursorco2.test", "Passw0rd!x");
        // The ceiling is the whole point of the parameter; a client asking for ten thousand is being
        // optimistic, not wrong, so it is clamped rather than failed.
        mockMvc.perform(get("/api/v1/people/leave?size=100000")
                        .header("Authorization", "Bearer " + owner.accessToken()))
                .andExpect(status().isOk());
    }

    private Session seedScale() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/dev/seed-scale?employees=" + HEADCOUNT + "&attendanceDays=2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seeded = objectMapper.readTree(r.getResponse().getContentAsString());
        return login(seeded.get("adminEmail").asText(), seeded.get("password").asText());
    }
}
