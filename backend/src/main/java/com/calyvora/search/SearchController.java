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

    private final com.calyvora.access.PermissionService permissions;

    public SearchController(SearchService searchService,
            com.calyvora.access.PermissionService permissions) {
        this.permissions = permissions;
        this.searchService = searchService;
    }

    @GetMapping
    public SearchResponse search(@RequestParam(name = "q", required = false) String q,
                                 @CurrentUser AuthPrincipal principal) {
        // Issued letters must not leak through the search box: only for those who issue them.
        boolean admin = permissions.has(principal, com.calyvora.access.Permission.DOCUMENTS_ISSUE);
        return searchService.search(q, admin);
    }
}
