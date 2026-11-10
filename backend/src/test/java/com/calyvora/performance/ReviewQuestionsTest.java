package com.calyvora.performance;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Structured reviews (PD-66): a cycle's questions, both sides answering them, the outcome — hike,
 * promotion, effective date — applied on approval with the letter issued, and cycles deletable until
 * something in them is decided.
 */
class ReviewQuestionsTest extends IntegrationTestBase {

    private static final String PW = "demopass123";

    private JsonNode send(String method, String path, Session s, Object body) throws Exception {
        var req = switch (method) {
            case "PATCH" -> patch(path);
            case "DELETE" -> delete(path);
            default -> post(path);
        };
        var res = mockMvc.perform(req.header("Authorization", "Bearer " + s.accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body == null ? "{}" : json(body)))
                .andReturn().getResponse();
        assertThat(res.getStatus()).as(method + " " + path + " → " + res.getContentAsString()).isLessThan(300);
        String text = res.getContentAsString();
        return text.isBlank() ? null : objectMapper.readTree(text);
    }

    @Test
    @DisplayName("questions are answered by both sides, and approval applies hike, title and letter from the effective date")
    void full_review() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session admin = login("ava.chen@northwind.demo", PW);
        Session priya = login("priya.nair@northwind.demo", PW);

        JsonNode cycle = send("POST", "/api/v1/performance/cycles", admin,
                Map.of("name", "H2 2026", "periodStart", "2026-04-01", "periodEnd", "2026-09-30"));
        JsonNode questions = cycle.get("questions");
        assertThat(questions.size()).as("the standard set").isEqualTo(ReviewForms.DEFAULTS.size());

        JsonNode mine = null;
        for (JsonNode r : getJson("/api/v1/performance/me/reviews", priya)) {
            if (r.get("cycleId").asText().equals(cycle.get("id").asText())) mine = r;
        }
        assertThat(mine).isNotNull();
        String reviewId = mine.get("id").asText();
        assertThat(mine.get("questions").size()).isEqualTo(questions.size());

        // Submitting with questions unanswered is refused, and says which.
        mockMvc.perform(patch("/api/v1/performance/reviews/" + reviewId + "/self")
                        .header("Authorization", "Bearer " + priya.accessToken()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("selfAssessment", "", "submit", true, "answers", Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Answer every question")));

        Map<String, Object> self = answers(questions, "SELF");
        send("PATCH", "/api/v1/performance/reviews/" + reviewId + "/self", priya,
                Map.of("selfAssessment", "", "submit", true, "answers", self));

        // The manager's side, with the outcome. (An admin may write it.)
        LocalDate effective = LocalDate.now().plusDays(10);
        Map<String, Object> mgr = new HashMap<>();
        mgr.put("rating", 4);
        mgr.put("summary", "A strong half.");
        mgr.put("hikeType", "PERCENT");
        mgr.put("hikePercent", 12);
        mgr.put("newTitle", "Senior Designer");
        mgr.put("effectiveDate", effective.toString());
        mgr.put("answers", answers(questions, "MANAGER"));
        mgr.put("submit", true);
        JsonNode submitted = send("PATCH", "/api/v1/performance/reviews/" + reviewId + "/manager", admin, mgr);
        assertThat(submitted.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(submitted.get("managerAnswers").size()).isPositive();
        assertThat(submitted.get("selfAnswers").size()).isPositive();
        String employeeId = submitted.get("employeeId").asText();

        JsonNode approved = send("POST", "/api/v1/performance/reviews/" + reviewId + "/approve", admin,
                Map.of("issueLetter", true));
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");

        JsonNode employee = getJson("/api/v1/people/employees/" + employeeId, admin);
        assertThat(employee.get("jobTitle").asText()).isEqualTo("Senior Designer");
        JsonNode comp = getJson("/api/v1/people/employees/" + employeeId + "/compensation", admin);
        assertThat(comp.toString()).contains(effective.toString());
        boolean letter = false;
        for (JsonNode d : getJson("/api/v1/documents?employeeId=" + employeeId, admin).get("items")) {
            if ("INCREMENT_LETTER".equals(d.get("kind").asText())) letter = true;
        }
        assertThat(letter).as("the increment letter was issued").isTrue();

        // A cycle with an approved review is kept.
        mockMvc.perform(delete("/api/v1/performance/cycles/" + cycle.get("id").asText())
                        .header("Authorization", "Bearer " + admin.accessToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("approval waits for the self-assessment unless forced; an undecided cycle can be deleted")
    void waits_and_deletes() throws Exception {
        mockMvc.perform(post("/api/v1/dev/seed-demo")).andExpect(status().isOk());
        Session admin = login("ava.chen@northwind.demo", PW);
        JsonNode cycle = send("POST", "/api/v1/performance/cycles", admin, Map.of("name", "Oops", "periodStart", "2026-01-01",
                "periodEnd", "2026-03-31", "questions", List.of(Map.of("text", "One thing that went well", "kind", "TEXT", "audience", "BOTH"))));
        assertThat(cycle.get("questions").size()).isEqualTo(1);
        JsonNode review = getJson("/api/v1/performance/cycles/" + cycle.get("id").asText() + "/reviews", admin).get(0);
        String id = review.get("id").asText();
        String qid = cycle.get("questions").get(0).get("id").asText();
        send("PATCH", "/api/v1/performance/reviews/" + id + "/manager", admin,
                Map.of("rating", 3, "submit", true, "answers", Map.of(qid, Map.of("text", "Shipped on time"))));
        mockMvc.perform(post("/api/v1/performance/reviews/" + id + "/approve")
                        .header("Authorization", "Bearer " + admin.accessToken()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("self-assessment")));

        send("DELETE", "/api/v1/performance/cycles/" + cycle.get("id").asText(), admin, null);
        assertThat(getJson("/api/v1/performance/cycles", admin).toString()).doesNotContain("\"Oops\"");
    }

    /** An answer to every question a side is asked: 4 for ratings, a sentence for written ones. */
    private static Map<String, Object> answers(JsonNode questions, String side) {
        Map<String, Object> out = new HashMap<>();
        for (JsonNode q : questions) {
            String aud = q.get("audience").asText();
            if (!aud.equals("BOTH") && !aud.equals(side)) continue;
            out.put(q.get("id").asText(), "RATING".equals(q.get("kind").asText())
                    ? Map.of("rating", 4) : Map.of("text", "Answer from " + side.toLowerCase()));
        }
        return out;
    }
}
