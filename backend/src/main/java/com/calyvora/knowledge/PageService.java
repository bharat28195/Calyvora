package com.calyvora.knowledge;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.knowledge.dto.CreatePageRequest;
import com.calyvora.knowledge.dto.PageResponse;
import com.calyvora.knowledge.dto.PageSummary;
import com.calyvora.knowledge.dto.UpdatePageRequest;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import com.calyvora.people.EmployeeService;
import com.calyvora.work.Project;
import com.calyvora.work.ProjectRepository;
import com.calyvora.work.Task;
import com.calyvora.work.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pages (Knowledge OS slices K2–K5). A page's author is a People OS {@link Employee} and a page may
 * link a Work OS {@link Task} — the cross-app knowledge graph. All access is tenant-scoped.
 */
@Service
public class PageService {

    private static final int SNIPPET_RADIUS = 80;

    private final PageRepository pageRepository;
    private final SpaceRepository spaceRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public PageService(PageRepository pageRepository, SpaceRepository spaceRepository,
                       EmployeeRepository employeeRepository, EmployeeService employeeService,
                       UserRepository userRepository, TaskRepository taskRepository,
                       ProjectRepository projectRepository) {
        this.pageRepository = pageRepository;
        this.spaceRepository = spaceRepository;
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public List<PageSummary> listForSpace(UUID spaceId) {
        Space space = requireSpace(spaceId);
        Cache cache = new Cache();
        return pageRepository.findBySpaceIdOrderBySortOrderAscCreatedAtAsc(spaceId).stream()
                .map(p -> summary(p, space.getName(), cache, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse get(UUID id) {
        Page page = require(id);
        Cache cache = new Cache();
        return PageResponse.of(page, cache.authorName(page.getAuthorId()), cache.taskRef(page.getLinkedTaskId()));
    }

    @Transactional
    public PageResponse create(UUID spaceId, CreatePageRequest request, AuthPrincipal principal) {
        Space space = requireSpace(spaceId);
        UUID companyId = space.getCompanyId();
        // Provision the author's People profile if needed, so authorship always resolves to a person.
        UUID authorId = employeeService.ensureEmployeeId(companyId, principal.userId());

        Page page = new Page(UUID.randomUUID(), companyId, spaceId, request.title().trim(),
                authorId, principal.userId());
        if (request.body() != null) {
            page.setBody(blankToNull(request.body()));
        }
        page.setParentId(resolveParent(companyId, spaceId, request.parentId(), null));
        page.setLinkedTaskId(resolveTask(companyId, request.linkedTaskId()));
        page.setSortOrder((int) pageRepository.countBySpaceId(spaceId));
        pageRepository.save(page);

        Cache cache = new Cache();
        return PageResponse.of(page, cache.authorName(authorId), cache.taskRef(page.getLinkedTaskId()));
    }

    @Transactional
    public PageResponse update(UUID id, UpdatePageRequest request) {
        Page page = require(id);
        UUID companyId = page.getCompanyId();

        if (request.title() != null && !request.title().isBlank()) {
            page.setTitle(request.title().trim());
        }
        if (request.body() != null) {
            page.setBody(blankToNull(request.body()));
        }
        if (request.status() != null && !request.status().isBlank()) {
            page.setStatus(PageStatus.valueOf(request.status()));
        }
        if (request.parentId() != null) {
            page.setParentId(resolveParent(companyId, page.getSpaceId(), request.parentId(), page.getId()));
        }
        if (request.linkedTaskId() != null) {
            page.setLinkedTaskId(resolveTask(companyId, request.linkedTaskId()));
        }

        Cache cache = new Cache();
        return PageResponse.of(page, cache.authorName(page.getAuthorId()), cache.taskRef(page.getLinkedTaskId()));
    }

    @Transactional
    public void delete(UUID id) {
        Page page = require(id);
        // Detach any children so the tree stays valid, then delete.
        for (Page child : pageRepository.findByParentId(page.getId())) {
            child.setParentId(null);
        }
        pageRepository.delete(page);
    }

    @Transactional(readOnly = true)
    public List<PageSummary> mine(AuthPrincipal principal) {
        Employee me = employeeRepository.findByUserId(principal.userId()).orElse(null);
        if (me == null) {
            return List.of();
        }
        Cache cache = new Cache();
        return pageRepository.findByAuthorIdOrderByUpdatedAtDesc(me.getId()).stream()
                .map(p -> summary(p, cache.spaceName(p.getSpaceId()), cache, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PageSummary> search(String query) {
        UUID companyId = TenantContext.getCompanyId();
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        Cache cache = new Cache();
        return pageRepository.search(companyId, q).stream()
                .map(p -> summary(p, cache.spaceName(p.getSpaceId()), cache, snippet(p.getBody(), q)))
                .toList();
    }

    // ---- helpers ----

    private Space requireSpace(UUID spaceId) {
        UUID companyId = TenantContext.getCompanyId();
        return spaceRepository.findByIdAndCompanyId(spaceId, companyId)
                .orElseThrow(() -> new NotFoundException("Space not found"));
    }

    private Page require(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        return pageRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Page not found"));
    }

    private PageSummary summary(Page p, String spaceName, Cache cache, String snippet) {
        return PageSummary.of(p, spaceName, cache.authorName(p.getAuthorId()),
                cache.taskRef(p.getLinkedTaskId()), snippet);
    }

    /** Validate an optional parent page (blank clears it); guard against self-parenting. */
    private UUID resolveParent(UUID companyId, UUID spaceId, String parentId, UUID selfId) {
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        UUID parent = parseId(parentId, "Invalid parent id");
        if (parent.equals(selfId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "A page cannot be its own parent");
        }
        Page target = pageRepository.findByIdAndCompanyId(parent, companyId)
                .orElseThrow(() -> new NotFoundException("Parent page not found"));
        if (!target.getSpaceId().equals(spaceId)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Parent must be in the same space");
        }
        return parent;
    }

    /** Validate an optional linked Work task (blank clears it) — the cross-app link into Work OS. */
    private UUID resolveTask(UUID companyId, String linkedTaskId) {
        if (linkedTaskId == null || linkedTaskId.isBlank()) {
            return null;
        }
        UUID taskId = parseId(linkedTaskId, "Invalid task id");
        taskRepository.findByIdAndCompanyId(taskId, companyId)
                .orElseThrow(() -> new NotFoundException("Linked task not found"));
        return taskId;
    }

    private static UUID parseId(String raw, String message) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, message);
        }
    }

    private static String snippet(String body, String q) {
        if (body == null || body.isBlank()) {
            return null;
        }
        int at = body.toLowerCase().indexOf(q.toLowerCase());
        if (at < 0) {
            return body.length() <= SNIPPET_RADIUS * 2 ? body : body.substring(0, SNIPPET_RADIUS * 2) + "…";
        }
        int start = Math.max(0, at - SNIPPET_RADIUS);
        int end = Math.min(body.length(), at + q.length() + SNIPPET_RADIUS);
        String core = body.substring(start, end);
        return (start > 0 ? "…" : "") + core + (end < body.length() ? "…" : "");
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Per-request memoization for the cross-app / cross-entity label lookups. */
    private final class Cache {
        private final Map<UUID, String> authors = new HashMap<>();
        private final Map<UUID, String> tasks = new HashMap<>();
        private final Map<UUID, String> spaces = new HashMap<>();

        String authorName(UUID authorId) {
            if (authorId == null) {
                return null;
            }
            return authors.computeIfAbsent(authorId, id -> employeeRepository.findById(id)
                    .flatMap(e -> userRepository.findById(e.getUserId()))
                    .map(User::fullName)
                    .orElse(null));
        }

        String taskRef(UUID taskId) {
            if (taskId == null) {
                return null;
            }
            return tasks.computeIfAbsent(taskId, id -> taskRepository.findById(id)
                    .map(t -> projectRepository.findById(t.getProjectId())
                            .map(Project::getKey).orElse("?") + "-" + t.getNumber())
                    .orElse(null));
        }

        String spaceName(UUID spaceId) {
            if (spaceId == null) {
                return null;
            }
            return spaces.computeIfAbsent(spaceId, id -> spaceRepository.findById(id)
                    .map(Space::getName).orElse(null));
        }
    }
}
