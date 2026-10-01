package com.calyvora.search;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.search.dto.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Global search across all three apps ({@code GET /api/v1/search?q=}). Tenant-scoped; auth required. */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(@RequestParam(name = "q", required = false) String q,
                                 @CurrentUser AuthPrincipal principal) {
        // Admin-only modules (Clients, Documents) must not leak through the search box either.
        boolean admin = "OWNER".equals(principal.role()) || "ADMIN".equals(principal.role());
        return searchService.search(q, admin);
    }
}
