package com.calyvora.knowledge.dto;

import com.calyvora.knowledge.Page;

/** Full page detail (includes body + resolved cross-app labels). */
public record PageResponse(
        String id,
        String spaceId,
        String parentId,
        String title,
        String body,
        String status,
        String authorId,
        String authorName,
        String linkedTaskId,
        String linkedTaskRef,
        String createdAt,
        String updatedAt
) {
    public static PageResponse of(Page p, String authorName, String linkedTaskRef) {
        return new PageResponse(
                p.getId().toString(),
                p.getSpaceId().toString(),
                p.getParentId() == null ? null : p.getParentId().toString(),
                p.getTitle(),
                p.getBody(),
                p.getStatus().name(),
                p.getAuthorId() == null ? null : p.getAuthorId().toString(),
                authorName,
                p.getLinkedTaskId() == null ? null : p.getLinkedTaskId().toString(),
                linkedTaskRef,
                p.getCreatedAt().toString(),
                p.getUpdatedAt().toString());
    }
}
