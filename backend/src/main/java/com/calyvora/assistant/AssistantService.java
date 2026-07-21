package com.calyvora.assistant;

import com.calyvora.assistant.dto.AssistantResponse;
import com.calyvora.assistant.dto.AssistantResponse.Source;
import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.security.TenantContext;
import com.calyvora.knowledge.Page;
import com.calyvora.knowledge.PageRepository;
import com.calyvora.knowledge.Space;
import com.calyvora.knowledge.SpaceRepository;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.people.EmployeeService;
import com.calyvora.people.dto.EmployeeResponse;
import com.calyvora.work.ProjectRepository;
import com.calyvora.work.TaskRepository;
import com.calyvora.work.TaskStatus;
import com.calyvora.work.TicketRepository;
import com.calyvora.work.TicketStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The cross-app AI assistant (the Phase-1 platform capability). It retrieves grounding from the
 * tenant's People, Work, and Knowledge data (RAG), then asks the best available provider — Claude
 * when a key is configured, the offline grounded provider otherwise — for an answer. Tenant-scoped:
 * it only ever sees the caller's own company.
 */
@Service
public class AssistantService {

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "are", "how", "many", "what", "who", "does", "did", "was", "were",
            "with", "from", "that", "this", "there", "here", "about", "into", "your", "our", "have",
            "has", "can", "will", "any", "all", "give", "show", "tell", "list", "count", "number");

    private final EmployeeService employeeService;
    private final DepartmentRepository departmentRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TicketRepository ticketRepository;
    private final SpaceRepository spaceRepository;
    private final PageRepository pageRepository;
    private final ClaudeAssistant claude;
    private final LocalGroundedAssistant local;

    public AssistantService(EmployeeService employeeService, DepartmentRepository departmentRepository,
                            ProjectRepository projectRepository, TaskRepository taskRepository,
                            TicketRepository ticketRepository, SpaceRepository spaceRepository,
                            PageRepository pageRepository, ClaudeAssistant claude,
                            LocalGroundedAssistant local) {
        this.employeeService = employeeService;
        this.departmentRepository = departmentRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.ticketRepository = ticketRepository;
        this.spaceRepository = spaceRepository;
        this.pageRepository = pageRepository;
        this.claude = claude;
        this.local = local;
    }

    @Transactional(readOnly = true)
    public AssistantResponse ask(String question) {
        if (question == null || question.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Ask a question first.");
        }
        UUID companyId = TenantContext.getCompanyId();
        AssistantContext ctx = buildContext(companyId, question.trim());

        // Prefer Claude when configured; fall back to the always-available local provider.
        String answer = null;
        String mode = local.mode();
        if (claude.available()) {
            answer = claude.answer(ctx);
            if (answer != null && !answer.isBlank()) {
                mode = claude.mode();
            }
        }
        if (answer == null || answer.isBlank()) {
            answer = local.answer(ctx);
        }
        return new AssistantResponse(answer, mode, ctx.sources());
    }

    private AssistantContext buildContext(UUID companyId, String question) {
        Map<String, Long> metrics = new LinkedHashMap<>();
        metrics.put("members", (long) employeeDirectory(companyId).size());
        metrics.put("departments", departmentRepository.countByCompanyId(companyId));
        metrics.put("projects", projectRepository.countByCompanyId(companyId));
        metrics.put("openTasks", taskRepository.countByCompanyIdAndStatusNot(companyId, TaskStatus.DONE));
        metrics.put("doneTasks", taskRepository.countByCompanyIdAndStatus(companyId, TaskStatus.DONE));
        metrics.put("openTickets", ticketRepository.countByCompanyIdAndStatusIn(companyId,
                List.of(TicketStatus.OPEN, TicketStatus.PENDING)));
        metrics.put("spaces", spaceRepository.countByCompanyId(companyId));
        metrics.put("pages", pageRepository.countByCompanyId(companyId));

        List<String> people = new ArrayList<>();
        for (EmployeeResponse e : employeeDirectory(companyId)) {
            String title = e.jobTitle() == null ? "" : " — " + e.jobTitle();
            people.add(e.firstName() + " " + e.lastName() + title);
        }

        // Retrieve the most relevant knowledge pages for the question.
        List<Page> pages = retrievePages(companyId, question);
        Map<UUID, Space> spaces = new LinkedHashMap<>();
        for (Space s : spaceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)) {
            spaces.put(s.getId(), s);
        }
        List<String> snippets = new ArrayList<>();
        List<Source> sources = new ArrayList<>();
        for (Page p : pages) {
            snippets.add("**" + p.getTitle() + "**\n" + snippet(p.getBody(), question));
            sources.add(new Source("page", p.getTitle(), "/knowledge/" + p.getSpaceId()));
        }

        return new AssistantContext(question, prompt(metrics, people, snippets), metrics, people, snippets, sources);
    }

    private List<EmployeeResponse> employeeDirectory(UUID companyId) {
        // TenantContext is already bound; directory() reads it.
        return employeeService.directory();
    }

    private List<Page> retrievePages(UUID companyId, String question) {
        Map<UUID, Integer> score = new LinkedHashMap<>();
        Map<UUID, Page> byId = new LinkedHashMap<>();
        for (String word : keywords(question)) {
            for (Page p : pageRepository.search(companyId, word)) {
                byId.putIfAbsent(p.getId(), p);
                score.merge(p.getId(), 1, Integer::sum);
            }
        }
        return score.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3)
                .map(e -> byId.get(e.getKey()))
                .toList();
    }

    private List<String> keywords(String question) {
        List<String> out = new ArrayList<>();
        for (String raw : question.toLowerCase().split("[^a-z0-9]+")) {
            if (raw.length() >= 3 && !STOPWORDS.contains(raw)) {
                out.add(raw);
            }
        }
        return out;
    }

    /** A ~240-char window around the first keyword hit, so answers cite the relevant passage. */
    private String snippet(String body, String question) {
        if (body == null || body.isBlank()) {
            return "(no content)";
        }
        String lower = body.toLowerCase();
        int at = -1;
        for (String word : keywords(question)) {
            int i = lower.indexOf(word);
            if (i >= 0) { at = i; break; }
        }
        int start = Math.max(0, at < 0 ? 0 : at - 80);
        int end = Math.min(body.length(), start + 240);
        String clip = body.substring(start, end).replaceAll("\\s+", " ").trim();
        return (start > 0 ? "…" : "") + clip + (end < body.length() ? "…" : "");
    }

    private String prompt(Map<String, Long> metrics, List<String> people, List<String> snippets) {
        StringBuilder sb = new StringBuilder();
        sb.append("COMPANY METRICS:\n");
        metrics.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append('\n'));
        if (!people.isEmpty()) {
            sb.append("\nPEOPLE:\n- ").append(String.join("\n- ", people)).append('\n');
        }
        if (!snippets.isEmpty()) {
            sb.append("\nRELEVANT KNOWLEDGE PAGES:\n\n").append(String.join("\n\n", snippets)).append('\n');
        }
        return sb.toString();
    }
}
