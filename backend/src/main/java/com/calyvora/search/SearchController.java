package com.calyvora.search;

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
    public SearchResponse search(@RequestParam(name = "q", required = false) String q) {
        return searchService.search(q);
    }
}
