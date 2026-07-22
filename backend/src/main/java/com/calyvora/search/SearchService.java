package com.calyvora.search;

import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.knowledge.PageRepository;
import com.calyvora.knowledge.Space;
import com.calyvora.knowledge.SpaceRepository;
import com.calyvora.search.dto.SearchResponse;
import com.calyvora.search.dto.SearchResponse.SearchGroup;
import com.calyvora.search.dto.SearchResponse.SearchHit;
import com.calyvora.work.Project;
import com.calyvora.work.ProjectRepository;
import com.calyvora.work.Task;
import com.calyvora.work.TaskRepository;
import com.calyvora.work.Ticket;
import com.calyvora.work.TicketRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One search box over the whole platform (SD — demo). Queries People, Work, and Knowledge in a
 * single tenant-scoped call and returns results grouped by app. Each source is capped so the box
 * stays fast and the response stays small; ranking is left to the per-type ordering for now.
 */
@Service
public class SearchService {

    private static final int PER_TYPE = 5;
    private static final Pageable LIMIT = PageRequest.of(0, PER_TYPE);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final TicketRepository ticketRepository;
    private final SpaceRepository spaceRepository;
    private final PageRepository pageRepository;
    private final com.calyvora.client.ClientRepository clientRepository;

    public SearchService(UserRepository userRepository, ProjectRepository projectRepository,
                         TaskRepository taskRepository, TicketRepository ticketRepository,
                         SpaceRepository spaceRepository, PageRepository pageRepository,
                         com.calyvora.client.ClientRepository clientRepository) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.ticketRepository = ticketRepository;
        this.spaceRepository = spaceRepository;
        this.pageRepository = pageRepository;
        this.clientRepository = clientRepository;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() < 2) {
            return new SearchResponse(q, 0, List.of());
        }
        UUID companyId = TenantContext.getCompanyId();

        // Lookup maps so Work/Knowledge hits can carry a human subtitle without N+1 queries.
        Map<UUID, Project> projects = projectRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<UUID, Space> spaces = spaceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().collect(Collectors.toMap(Space::getId, Function.identity()));

        List<SearchHit> people = new ArrayList<>();
        for (User u : userRepository.search(companyId, q, LIMIT)) {
            people.add(new SearchHit("person", u.fullName(), u.getEmail(), "/people"));
        }

        List<SearchHit> work = new ArrayList<>();
        for (Project p : projectRepository.search(companyId, q, LIMIT)) {
            work.add(new SearchHit("project", p.getName(), "Project · " + p.getKey(), "/work/" + p.getId()));
        }
        for (Task t : taskRepository.search(companyId, q, LIMIT)) {
            Project p = projects.get(t.getProjectId());
            String ref = p == null ? "Task" : p.getKey() + "-" + t.getNumber();
            String project = p == null ? "" : " · " + p.getName();
            work.add(new SearchHit("task", t.getTitle(), ref + project, "/work/" + t.getProjectId()));
        }
        for (Ticket t : ticketRepository.search(companyId, q, LIMIT)) {
            Project p = projects.get(t.getProjectId());
            String ref = p == null ? "Ticket" : p.getKey() + "-T" + t.getNumber();
            work.add(new SearchHit("ticket", t.getSubject(), ref, "/work/" + t.getProjectId()));
        }

        List<SearchHit> knowledge = new ArrayList<>();
        for (Space s : spaceRepository.search(companyId, q, LIMIT)) {
            knowledge.add(new SearchHit("space", s.getName(), "Space · " + s.getKey(), "/knowledge/" + s.getId()));
        }
        pageRepository.search(companyId, q).stream().limit(PER_TYPE).forEach(page -> {
            Space s = spaces.get(page.getSpaceId());
            knowledge.add(new SearchHit("page", page.getTitle(),
                    s == null ? "Page" : s.getName(), "/knowledge/" + page.getSpaceId()));
        });

        List<SearchHit> clients = new ArrayList<>();
        for (com.calyvora.client.Client c : clientRepository.search(companyId, q, LIMIT)) {
            clients.add(new SearchHit("client", c.getName(),
                    c.getContactName() == null ? "Client" : c.getContactName(), "/clients/" + c.getId()));
        }

        List<SearchGroup> groups = new ArrayList<>();
        if (!people.isEmpty()) groups.add(new SearchGroup("People", people));
        if (!work.isEmpty()) groups.add(new SearchGroup("Work", work));
        if (!knowledge.isEmpty()) groups.add(new SearchGroup("Knowledge", knowledge));
        if (!clients.isEmpty()) groups.add(new SearchGroup("Clients", clients));

        int total = people.size() + work.size() + knowledge.size() + clients.size();
        return new SearchResponse(q, total, groups);
    }
}
