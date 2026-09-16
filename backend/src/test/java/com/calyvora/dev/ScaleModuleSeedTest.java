package com.calyvora.dev;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every module the scale tenant has a screen for has rows in it.
 *
 * <p>The point is not the counts, it is the absence of zeroes. Scaleworks existed as a thousand
 * people and eighteen empty screens, and nothing failed — the seeder was doing exactly what it was
 * written to do. Walking the same endpoints the app walks is what turns "nobody seeded this" into a
 * failure here rather than a discovery mid-demo.
 *
 * <p>Each row names the field the list lives in, and the resolved node must actually be an array.
 * The first draft guessed at the wrapper and fell back to the whole object, which made a five-field
 * response "not empty" — a test that passed for a screen with no data in it. Naming the field is
 * the difference between checking the data and checking that a response exists.
 */
class ScaleModuleSeedTest extends IntegrationTestBase {

    /** endpoint → the field holding the list, or null when the response is the array itself. */
    private record Listing(String path, String field) {}

    private static final List<Listing> MUST_NOT_BE_EMPTY = List.of(
            new Listing("/api/v1/people/leave", null),
            new Listing("/api/v1/expenses", "claims"),
            new Listing("/api/v1/work/projects", null),
            new Listing("/api/v1/knowledge/spaces", null),
            new Listing("/api/v1/helpdesk/tickets", null),
            new Listing("/api/v1/feed", null),
            new Listing("/api/v1/performance/cycles", null),
            new Listing("/api/v1/recruit/jobs", null),
            new Listing("/api/v1/clients", null),
            new Listing("/api/v1/shifts", null),
            new Listing("/api/v1/shifts/roster", "assignments"),
            new Listing("/api/v1/attendance/regularizations/pending", null),
            new Listing("/api/v1/people/holidays", null),
            new Listing("/api/v1/people/employees", null));

    @Test
    @DisplayName("the scale tenant has data in every module, not just People")
    void every_module_has_something_in_it() throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/dev/seed-scale?employees=60&attendanceDays=2"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode seeded = objectMapper.readTree(r.getResponse().getContentAsString());
        Session admin = login(seeded.get("adminEmail").asText(), seeded.get("password").asText());

        try {
            for (Listing listing : MUST_NOT_BE_EMPTY) {
                JsonNode rows = rowsOf(getJson(listing.path(), admin), listing.field());
                assertThat(rows.isArray())
                        .as("%s should answer with a list%s", listing.path(),
                                listing.field() == null ? "" : " in '" + listing.field() + "'")
                        .isTrue();
                assertThat(rows.size()).as("%s should not be empty", listing.path()).isPositive();
            }

            // A queue with nothing waiting cannot be demonstrated, so the seeder leaves work in each
            // one deliberately. These are the screens somebody is asked to *act* on, and they are the
            // ones an empty tenant fails at most visibly.
            assertThat(countWhere(rowsOf(getJson("/api/v1/people/leave", admin), null), "PENDING"))
                    .as("leave waiting for a decision").isPositive();
            assertThat(countWhere(rowsOf(getJson("/api/v1/expenses", admin), "claims"), "SUBMITTED"))
                    .as("expenses waiting for a decision").isPositive();
            assertThat(countWhere(rowsOf(getJson("/api/v1/helpdesk/tickets", admin), null), "OPEN"))
                    .as("tickets waiting to be picked up").isPositive();
        } finally {
            // Never leave an invented thousand-person company behind, even on a failure.
            mockMvc.perform(delete("/api/v1/dev/seed-scale")).andExpect(status().isOk());
        }
    }

    private static JsonNode rowsOf(JsonNode body, String field) {
        return field == null ? body : body.path(field);
    }

    private static int countWhere(JsonNode rows, String status) {
        int n = 0;
        for (JsonNode row : rows) {
            if (status.equals(row.path("status").asText())) {
                n++;
            }
        }
        return n;
    }
}
