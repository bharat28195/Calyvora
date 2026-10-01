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

    /** A metric this question is asking for: which key, and how to say it in a sentence. */
    private record Wanted(String key, String noun) {}

    /**
     * Question to metric. Order matters — the most specific phrasings are tested first, because
     * "how many open roles" contains "role" and "how many people are leaving" contains "people".
     */
    private Wanted want(String q) {
        // --- people ops ---
        if (q.contains("notice") || q.contains("resign") || q.contains("exit") || q.contains("leaving")) {
            return new Wanted("peopleOnNotice", "person serving notice");
        }
        if (q.contains("onboard") || q.contains("joining") || q.contains("new joiner")) {
            return new Wanted("peopleOnboarding", "person still onboarding");
        }
        if (q.contains("leave") || q.contains("time off") || q.contains("holiday request")) {
            return new Wanted("pendingLeaveRequests", "time-off request waiting for approval");
        }
        if (q.contains("holiday")) return new Wanted("holidays", "public holiday");
        if (q.contains("expense") || q.contains("claim") || q.contains("reimburse")) {
            return new Wanted("pendingExpenseClaims", "expense claim waiting to be decided");
        }
        // --- hiring ---
        if (q.contains("candidate") || q.contains("applicant") || q.contains("pipeline")) {
            return new Wanted("candidatesInPipeline", "candidate still in the pipeline");
        }
        if (q.contains("opening") || q.contains("vacancy") || q.contains("hiring") || q.contains("role")) {
            return new Wanted("openRoles", "open role");
        }
        // --- the rest of HR ---
        if (q.contains("invite") || q.contains("invitation")) {
            return new Wanted("pendingInvitations", "invitation still unaccepted");
        }
        if (q.contains("review") || q.contains("appraisal")) {
            return new Wanted("openReviewCycles", "review cycle running");
        }
        if (q.contains("letter") || q.contains("template")) return new Wanted("letterTemplates", "letter template");
        if (q.contains("client")) return new Wanted("clients", "client");
        if (q.contains("helpdesk")) return new Wanted("openHelpdeskTickets", "open helpdesk ticket");
        // --- work and knowledge (the original four) ---
        if (q.contains("ticket")) return new Wanted("openTickets", "open support ticket");
        if (q.contains("task")) return new Wanted("openTasks", "open task");
        if (q.contains("project")) return new Wanted("projects", "project");
        if (q.contains("page") || q.contains("doc")) return new Wanted("pages", "knowledge page");
        if (q.contains("space")) return new Wanted("spaces", "knowledge space");
        // Check people before departments so "team members" resolves to headcount, not departments.
        if (q.contains("employee") || q.contains("people") || q.contains("member")
                || q.contains("staff") || q.contains("team")) {
            return new Wanted("members", "team member");
        }
        if (q.contains("department")) return new Wanted("departments", "department");
        return null;
    }

    private String matchMetric(String q, Map<String, Long> m) {
        Wanted w = want(q);
        if (w == null) {
            return null;
        }
        // The metric map holds only what this caller's role may be told. A missing key therefore
        // means "not yours to see" — and must not be answered as zero. Saying "you have 0 people on
        // notice" to someone who simply isn't allowed to know is worse than refusing: it is a
        // confident, wrong answer, and it leaks the shape of the data anyway.
        if (!m.containsKey(w.key())) {
            return "I can't see that for your role — it's kept to HR and admins. "
                    + "Ask them, or an admin can change your access.";
        }
        long n = m.get(w.key());
        return "You have **" + n + "** " + w.noun() + (n == 1 ? "." : "s.");
    }

    /**
     * The fallback answer. Every line is conditional on the metric actually being present, so the
     * overview a MEMBER sees is simply shorter than the one an admin sees — rather than the same
     * shape padded with zeroes for the things they aren't allowed to know.
     */
    private String overview(Map<String, Long> m) {
        StringBuilder sb = new StringBuilder("Here's your company at a glance:\n\n");
        sb.append("- **").append(m.getOrDefault("members", 0L)).append("** team members across **")
          .append(m.getOrDefault("departments", 0L)).append("** departments\n");
        sb.append("- **").append(m.getOrDefault("openTasks", 0L)).append("** open tasks and **")
          .append(m.getOrDefault("openTickets", 0L)).append("** open tickets in **")
          .append(m.getOrDefault("projects", 0L)).append("** project(s)\n");

        if (m.containsKey("pendingLeaveRequests") || m.containsKey("pendingExpenseClaims")) {
            sb.append("- **").append(m.getOrDefault("pendingLeaveRequests", 0L))
              .append("** time-off request(s) and **").append(m.getOrDefault("pendingExpenseClaims", 0L))
              .append("** expense claim(s) waiting on an approver\n");
        }
        if (m.containsKey("peopleOnNotice") || m.containsKey("peopleOnboarding")) {
            sb.append("- **").append(m.getOrDefault("peopleOnboarding", 0L)).append("** onboarding, **")
              .append(m.getOrDefault("peopleOnNotice", 0L)).append("** serving notice\n");
        }
        if (m.containsKey("openRoles")) {
            sb.append("- **").append(m.getOrDefault("openRoles", 0L)).append("** open role(s) with **")
              .append(m.getOrDefault("candidatesInPipeline", 0L)).append("** candidate(s) in the pipeline\n");
        }
        if (m.containsKey("openHelpdeskTickets")) {
            sb.append("- **").append(m.getOrDefault("openHelpdeskTickets", 0L))
              .append("** open helpdesk ticket(s) and **").append(m.getOrDefault("openReviewCycles", 0L))
              .append("** review cycle(s) running\n");
        }
        sb.append("- **").append(m.getOrDefault("pages", 0L)).append("** knowledge pages in **")
          .append(m.getOrDefault("spaces", 0L)).append("** space(s)\n\n");

        sb.append(m.containsKey("openRoles")
                ? "Ask me about your people, time off, hiring, approvals, letters — or anything in your docs."
                : "Ask me about your team, your work, time off, or anything in your docs.");
        return sb.toString();
    }
}
