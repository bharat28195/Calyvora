package com.calyvora.search.dto;

import java.util.List;

/**
 * Unified global-search result across People, Work, and Knowledge — grouped by app so the UI can
 * render sections. {@code total} is the number of hits returned (already capped per type).
 */
public record SearchResponse(String query, int total, List<SearchGroup> groups) {

    public record SearchGroup(String label, List<SearchHit> hits) {}

    /**
     * One result. {@code kind} is a fine-grained type (person/project/task/ticket/space/page) the UI
     * uses to pick an icon; {@code href} is the in-app route to open it.
     */
    public record SearchHit(String kind, String title, String subtitle, String href) {}
}
