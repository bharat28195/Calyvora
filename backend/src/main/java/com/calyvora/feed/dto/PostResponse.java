package com.calyvora.feed.dto;

import java.util.List;
import java.util.Map;

/** A post with everything the feed needs to render it in one pass. */
public record PostResponse(
        String id,
        String authorId,
        String authorName,
        String authorTitle,
        String kind,
        String body,
        String visibility,
        String departmentId,
        String departmentName,
        boolean pinned,
        /** emoji -> how many people used it. */
        Map<String, Long> reactions,
        /** The emoji the viewer has used, so the UI can show their choice as active. */
        List<String> myReactions,
        List<CommentResponse> comments,
        boolean canManage,
        String createdAt
) {
    /** A comment, with its author resolved. */
    public record CommentResponse(
            String id,
            String authorId,
            String authorName,
            String body,
            boolean canDelete,
            String createdAt
    ) {}
}
