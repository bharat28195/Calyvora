package com.calyvora.knowledge.dto;

import com.calyvora.knowledge.Space;

public record SpaceResponse(
        String id,
        String name,
        String key,
        String description,
        String status,
        long pageCount,
        String createdAt
) {
    public static SpaceResponse of(Space s, long pageCount) {
        return new SpaceResponse(
                s.getId().toString(), s.getName(), s.getKey(), s.getDescription(),
                s.getStatus().name(), pageCount, s.getCreatedAt().toString());
    }
}
