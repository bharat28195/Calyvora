package com.calyvora.knowledge.dto;

import com.calyvora.knowledge.CompanyFile;

/** A file in a company documents folder, as a listing shows it. The bytes come from the download URL. */
public record CompanyFileResponse(
        String id,
        String spaceId,
        String title,
        String fileName,
        String contentType,
        long sizeBytes,
        String uploadedByName,
        String createdAt
) {
    public static CompanyFileResponse of(CompanyFile f, String uploadedByName) {
        return new CompanyFileResponse(f.getId().toString(), f.getSpaceId().toString(), f.getTitle(),
                f.getFileName(), f.getContentType(), f.getSizeBytes(), uploadedByName,
                f.getCreatedAt().toString());
    }
}
