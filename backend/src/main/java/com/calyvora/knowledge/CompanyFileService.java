package com.calyvora.knowledge;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.knowledge.dto.CompanyFileResponse;
import com.calyvora.knowledge.storage.FileStorage;
import com.calyvora.knowledge.storage.FileStorageRouter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Files in the company documents area. Publishers upload and delete; everyone in the company lists
 * and downloads. Access to the write side is enforced at the controller ({@link DocumentAccess}).
 */
@Service
public class CompanyFileService {

    /** Per file. Matches spring.servlet.multipart.max-file-size, which refuses larger requests first. */
    static final long MAX_BYTES = 10L * 1024 * 1024;

    /**
     * What a company may keep in the database before it has to move to R2. Neon's free tier is 0.5 GB
     * for everything; without a cap one enthusiastic HR team could fill it with scanned PDFs.
     */
    static final long DB_QUOTA_BYTES = 100L * 1024 * 1024;

    /** Documents people actually share, by extension and the type browsers send for them. */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"));

    private final CompanyFileRepository files;
    private final SpaceRepository spaces;
    private final UserRepository users;
    private final FileStorageRouter storage;

    public CompanyFileService(CompanyFileRepository files, SpaceRepository spaces, UserRepository users,
                              FileStorageRouter storage) {
        this.files = files;
        this.spaces = spaces;
        this.users = users;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<CompanyFileResponse> list(UUID spaceId) {
        UUID companyId = TenantContext.getCompanyId();
        requireSpace(companyId, spaceId);
        return files.findByCompanyIdAndSpaceIdOrderByCreatedAtDesc(companyId, spaceId).stream()
                .map(f -> CompanyFileResponse.of(f, nameOf(f.getUploadedBy())))
                .toList();
    }

    @Transactional
    public CompanyFileResponse upload(UUID spaceId, String title, MultipartFile upload, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        requireSpace(companyId, spaceId);
        if (upload == null || upload.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Choose a file to upload");
        }
        if (upload.getSize() > MAX_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Files can be up to 10 MB");
        }
        String original = upload.getOriginalFilename() == null ? "file" : upload.getOriginalFilename();
        String fileName = original.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (fileName.isEmpty()) fileName = "file";
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String contentType = ALLOWED.get(ext);
        if (contentType == null) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "That type of file can't be uploaded. Use PDF, Word, Excel, PowerPoint, text, CSV or an image.");
        }
        FileStorage target = storage.forNewFile();
        if ("DB".equals(target.kind())
                && files.bytesStoredInDatabase(companyId) + upload.getSize() > DB_QUOTA_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Your company has used its 100 MB of document storage. Delete old files to make room.");
        }
        byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "The file could not be read");
        }

        String cleanTitle = title == null || title.isBlank()
                ? fileName.replaceFirst("\\.[^.]+$", "")
                : title.trim();
        if (cleanTitle.length() > 200) cleanTitle = cleanTitle.substring(0, 200);

        CompanyFile file = new CompanyFile(UUID.randomUUID(), companyId, spaceId, cleanTitle, fileName,
                contentType, bytes.length, principal.userId());
        // The row first, so the blob's foreign key has something to point at.
        file.setStorage(target.kind(), null);
        files.saveAndFlush(file);
        String key = target.put(file, bytes);
        if (key != null) {
            file.setStorage(target.kind(), key);
        }
        return CompanyFileResponse.of(file, nameOf(principal.userId()));
    }

    /** The file's row and bytes, for streaming to the caller. */
    @Transactional(readOnly = true)
    public Download download(UUID fileId) {
        CompanyFile file = require(fileId);
        return new Download(file, storage.forExisting(file).get(file));
    }

    @Transactional
    public void delete(UUID fileId) {
        CompanyFile file = require(fileId);
        storage.forExisting(file).delete(file);
        files.delete(file);
    }

    public record Download(CompanyFile file, byte[] bytes) {
    }

    private CompanyFile require(UUID fileId) {
        return files.findByIdAndCompanyId(fileId, TenantContext.getCompanyId())
                .orElseThrow(() -> new NotFoundException("File not found"));
    }

    private void requireSpace(UUID companyId, UUID spaceId) {
        spaces.findByIdAndCompanyId(spaceId, companyId)
                .orElseThrow(() -> new NotFoundException("Folder not found"));
    }

    private String nameOf(UUID userId) {
        return userId == null ? null : users.findById(userId).map(User::fullName).orElse(null);
    }
}
