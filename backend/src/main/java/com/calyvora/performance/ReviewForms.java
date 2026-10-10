package com.calyvora.performance;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The questions a review cycle asks and the answers given to them (PD-66).
 *
 * <p>A cycle carries its own copy of its questions, so editing next year's set never rewrites last
 * year's reviews. Answers are keyed by question id. Stored as JSON text: a review form is read and
 * written whole, never queried by answer.
 */
public final class ReviewForms {

    private ReviewForms() {
    }

    /** RATING is 1–5; TEXT is written. SELF is asked of the employee, MANAGER of the manager, BOTH of each. */
    public record Question(String id, String text, String kind, String audience) {
    }

    public record Answer(Integer rating, String text) {
    }

    private static final Set<String> KINDS = Set.of("RATING", "TEXT");
    private static final Set<String> AUDIENCES = Set.of("SELF", "MANAGER", "BOTH");
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * The standard set every new cycle starts with — the things both sides of a fair review talk
     * about, a mix of ratings (comparable across a team) and words (which say why). HR edits it per
     * cycle.
     */
    public static final List<Question> DEFAULTS = List.of(
            new Question("achievements", "Key achievements this period", "TEXT", "BOTH"),
            new Question("goals", "How well were the period's goals met?", "RATING", "BOTH"),
            new Question("quality", "Ownership and quality of work", "RATING", "BOTH"),
            new Question("teamwork", "Collaboration and teamwork", "RATING", "BOTH"),
            new Question("strengths", "Strengths", "TEXT", "BOTH"),
            new Question("improve", "Areas to improve", "TEXT", "BOTH"),
            new Question("support", "Support or training wanted for the next period", "TEXT", "SELF"),
            new Question("readiness", "Readiness for more responsibility or a promotion", "TEXT", "MANAGER"));

    /** Checks and tidies an edited set: ids unique, kinds and audiences known, at most 30 questions. */
    public static List<Question> clean(List<Question> questions) {
        if (questions == null || questions.isEmpty()) return DEFAULTS;
        if (questions.size() > 30) throw invalid("A review can have at most 30 questions.");
        List<Question> out = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        int n = 1;
        for (Question q : questions) {
            String text = q.text() == null ? "" : q.text().trim();
            if (text.isEmpty()) continue;
            if (text.length() > 300) throw invalid("Keep each question under 300 characters.");
            String kind = q.kind() == null ? "TEXT" : q.kind().toUpperCase(java.util.Locale.ROOT);
            String audience = q.audience() == null ? "BOTH" : q.audience().toUpperCase(java.util.Locale.ROOT);
            if (!KINDS.contains(kind)) throw invalid("A question is either a rating or written.");
            if (!AUDIENCES.contains(audience)) throw invalid("A question is for the employee, the manager or both.");
            String id = q.id() == null || q.id().isBlank() ? "q" + n : q.id().trim();
            while (!ids.add(id)) id = id + "_";
            out.add(new Question(id, text, kind, audience));
            n++;
        }
        if (out.isEmpty()) throw invalid("Add at least one question.");
        return List.copyOf(out);
    }

    public static String write(Object value) {
        try {
            return value == null ? null : JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<Question> questions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<Question>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    public static Map<String, Answer> answers(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, Answer>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * The answers kept for one side: only questions that side is asked, ratings 1–5, text trimmed.
     * With {@code complete}, every question for that side must be answered (a submit).
     */
    public static Map<String, Answer> forSide(List<Question> questions, Map<String, Answer> given, boolean manager,
                                              boolean complete) {
        Map<String, Answer> out = new LinkedHashMap<>();
        for (Question q : questions) {
            boolean asked = "BOTH".equals(q.audience()) || (manager ? "MANAGER" : "SELF").equals(q.audience());
            if (!asked) continue;
            Answer a = given == null ? null : given.get(q.id());
            Integer rating = null;
            String text = null;
            if (a != null) {
                if ("RATING".equals(q.kind()) && a.rating() != null) {
                    if (a.rating() < 1 || a.rating() > 5) throw invalid("Ratings are from 1 to 5.");
                    rating = a.rating();
                }
                if (a.text() != null && !a.text().isBlank()) {
                    text = a.text().trim();
                    if (text.length() > 5000) throw invalid("Keep each answer under 5,000 characters.");
                }
            }
            boolean answered = "RATING".equals(q.kind()) ? rating != null : text != null;
            if (complete && !answered) throw invalid("Answer every question before submitting: \"" + q.text() + "\"");
            if (rating != null || text != null) out.put(q.id(), new Answer(rating, text));
        }
        return out;
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }
}
