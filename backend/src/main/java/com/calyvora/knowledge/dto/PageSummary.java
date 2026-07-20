package com.calyvora.knowledge.dto;

import com.calyvora.knowledge.Page;

/** Lightweight page row for trees, "my pages", and search results (no full body — optional snippet). */
public record PageSummary(
        String id,
        String spaceId,
        String spaceName,
        String parentId,
        String title,
        String status,
        String authorName,
        String linkedTaskRef,
        String snippet,
        String updatedAt
) {
    public static PageSummary of(Page p, String spaceName, String authorName, String linkedTaskRef, String snippet) {
        return new PageSummary(
                p.getId().toString(),
                p.getSpaceId().toString(),
                spaceName,
                p.getParentId() == null ? null : p.getParentId().toString(),
                p.getTitle(),
                p.getStatus().name(),
                authorName,
                linkedTaskRef,
                snippet,
                p.getUpdatedAt().toString());
    }
}
