package com.calyvora.assistant;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The offline assistant: no API key, no network — it answers from the retrieved context using
 * lightweight intent matching. It never fabricates: count questions are answered from real metrics,
 * knowledge questions from real page snippets, and anything else from a grounded overview. This is
 * what makes the demo safe on stage — it cannot fail, and it gets smarter the moment a key is added.
 */
@Component
class LocalGroundedAssistant implements AssistantProvider {

    @Override
    public boolean available() {
        return true;   // always the safety net
    }

    @Override
    public String mode() {
        return "local";
    }

    @Override
    public String answer(AssistantContext ctx) {
        String q = ctx.question().toLowerCase();
        Map<String, Long> m = ctx.metrics();

        // 1) Counting / "how many" questions → answer from real metrics.
        if (q.contains("how many") || q.contains("number of") || q.contains("count")
                || q.startsWith("how much")) {
            String metric = matchMetric(q, m);
            if (metric != null) {
                return metric;
            }
        }

        // 2) "Who" / people questions → the directory.
        if ((q.contains("who") || q.contains("people") || q.contains("team") || q.contains("employees"))
                && !ctx.people().isEmpty()) {
            return "Here's who's on the team:\n\n- " + String.join("\n- ", ctx.people());
        }

        // 3) Knowledge questions → grounded snippets from real pages.
        if (!ctx.snippets().isEmpty()) {
            String body = String.join("\n\n", ctx.snippets().subList(0, Math.min(2, ctx.snippets().size())));
            return "Here's what I found in your Knowledge base:\n\n" + body;
        }

        // 4) Fallback → a grounded status overview.
        return overview(m);
    }

    private String matchMetric(String q, Map<String, Long> m) {
        if (q.contains("ticket")) return sentence(m, "openTickets", "open support ticket");
        if (q.contains("task")) return sentence(m, "openTasks", "open task");
        if (q.contains("project")) return sentence(m, "projects", "project");
        if (q.contains("page") || q.contains("doc")) return sentence(m, "pages", "knowledge page");
        if (q.contains("space")) return sentence(m, "spaces", "knowledge space");
        // Check people before departments so "team members" resolves to headcount, not departments.
        if (q.contains("employee") || q.contains("people") || q.contains("member")
                || q.contains("staff") || q.contains("team")) {
            return sentence(m, "members", "team member");
        }
        if (q.contains("department")) return sentence(m, "departments", "department");
        return null;
    }

    private String sentence(Map<String, Long> m, String key, String noun) {
        long n = m.getOrDefault(key, 0L);
        return "You have **" + n + "** " + noun + (n == 1 ? "." : "s.");
    }

    private String overview(Map<String, Long> m) {
        return "Here's your company at a glance:\n\n"
                + "- **" + m.getOrDefault("members", 0L) + "** team members across **"
                + m.getOrDefault("departments", 0L) + "** departments\n"
                + "- **" + m.getOrDefault("openTasks", 0L) + "** open tasks and **"
                + m.getOrDefault("openTickets", 0L) + "** open tickets in **"
                + m.getOrDefault("projects", 0L) + "** project(s)\n"
                + "- **" + m.getOrDefault("pages", 0L) + "** knowledge pages in **"
                + m.getOrDefault("spaces", 0L) + "** space(s)\n\n"
                + "Ask me about a person, a project, a ticket, or anything in your docs.";
    }
}
