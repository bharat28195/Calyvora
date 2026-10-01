package com.calyvora.knowledge.dto;

import jakarta.validation.constraints.Size;

/**
 * Partial page update. {@code null} means "leave unchanged"; blank strings for the id fields
 * ({@code parentId}, {@code linkedTaskId}) mean "clear the link".
 */
public record UpdatePageRequest(
        @Size(min = 1, max = 200) String title,
        String body,
        String status,
        String parentId,
        String linkedTaskId
) {
}
