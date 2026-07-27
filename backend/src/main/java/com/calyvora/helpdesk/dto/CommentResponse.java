package com.calyvora.helpdesk.dto;

import com.calyvora.helpdesk.HelpdeskComment;

import java.util.Map;
import java.util.UUID;

/** One thread message with its author's display name. */
public record CommentResponse(String id, String authorId, String authorName, String body, String createdAt) {

    public static CommentResponse of(HelpdeskComment c, Map<UUID, String> names) {
        return new CommentResponse(c.getId().toString(), c.getAuthorId().toString(),
                names.getOrDefault(c.getAuthorId(), "Someone"), c.getBody(), c.getCreatedAt().toString());
    }
}
