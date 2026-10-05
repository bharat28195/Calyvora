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
    private final SpaceRepository spaceRepository;
    private final PageRepository pageRepository;
    private final com.calyvora.document.GeneratedDocumentRepository documentRepository;

    public SearchService(UserRepository userRepository,
                         SpaceRepository spaceRepository, PageRepository pageRepository,
                         com.calyvora.document.GeneratedDocumentRepository documentRepository) {
        this.userRepository = userRepository;
        this.spaceRepository = spaceRepository;
        this.pageRepository = pageRepository;
        this.documentRepository = documentRepository;
    }

    /**
     * @param admin whether the caller may see Owner/Admin-only modules (issued documents). Search
     *              must not become a side door around the role gates on those APIs.
     */
    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery, boolean admin) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        if (q.length() < 2) {
            return new SearchResponse(q, 0, List.of());
        }
        UUID companyId = TenantContext.getCompanyId();

        // Lookup map so page hits can carry their space's name without N+1 queries.
        Map<UUID, Space> spaces = spaceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream().collect(Collectors.toMap(Space::getId, Function.identity()));

        List<SearchHit> people = new ArrayList<>();
        for (User u : userRepository.search(companyId, q, LIMIT)) {
            people.add(new SearchHit("person", u.fullName(), u.getEmail(), "/people"));
        }

        List<SearchHit> knowledge = new ArrayList<>();
        for (Space s : spaceRepository.search(companyId, q, LIMIT)) {
            knowledge.add(new SearchHit("space", s.getName(), "Space · " + s.getKey(), "/knowledge/" + s.getId()));
        }
        // Published only: a draft company document is not there yet for search.
        pageRepository.search(companyId, q).stream()
                .filter(p -> p.getStatus() == com.calyvora.knowledge.PageStatus.PUBLISHED)
                .limit(PER_TYPE).forEach(page -> {
            Space s = spaces.get(page.getSpaceId());
            knowledge.add(new SearchHit("page", page.getTitle(),
                    s == null ? "Page" : s.getName(), "/knowledge/" + page.getSpaceId()));
        });

        List<SearchHit> documents = new ArrayList<>();
        if (admin) {
            for (com.calyvora.document.GeneratedDocument d : documentRepository.search(companyId, q, LIMIT)) {
                documents.add(new SearchHit("document", d.getTitle(),
                        d.getKind().name().replace('_', ' ').toLowerCase(), "/documents/" + d.getId()));
            }
        }

        List<SearchGroup> groups = new ArrayList<>();
        if (!people.isEmpty()) groups.add(new SearchGroup("People", people));
        if (!knowledge.isEmpty()) groups.add(new SearchGroup("Knowledge", knowledge));
        if (!documents.isEmpty()) groups.add(new SearchGroup("Documents", documents));

        int total = people.size() + knowledge.size() + documents.size();
        return new SearchResponse(q, total, groups);
    }
}
