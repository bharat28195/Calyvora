package com.calyvora.knowledge;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.knowledge.dto.CompanyFileResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Files in the company documents area. Everyone in the company lists and downloads; publishers
 * ({@link DocumentAccess}) upload and delete.
 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class CompanyFileController {

    /**
     * Types a browser may show in a tab. Everything else downloads: an HTML or SVG "document" opened
     * inline on our origin would be a stored-XSS hole, and none is on the allowed list anyway — this
     * keeps it that way if the list ever grows.
     */
    private static final Set<String> INLINE = Set.of("application/pdf", "image/png", "image/jpeg");

    private final CompanyFileService service;

    public CompanyFileController(CompanyFileService service) {
        this.service = service;
    }

    @GetMapping("/spaces/{spaceId}/files")
    public List<CompanyFileResponse> list(@PathVariable UUID spaceId) {
        return service.list(spaceId);
    }

    @PostMapping(value = "/spaces/{spaceId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@documentAccess.canPublish()")
    public ResponseEntity<CompanyFileResponse> upload(@PathVariable UUID spaceId,
                                                      @RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "title", required = false) String title,
                                                      @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(spaceId, title, file, principal));
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID fileId,
                                           @RequestParam(value = "inline", defaultValue = "false") boolean inline) {
        CompanyFileService.Download d = service.download(fileId);
        boolean showInline = inline && INLINE.contains(d.file().getContentType());
        ContentDisposition disposition = (showInline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(d.file().getFileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(d.file().getContentType()))
                .contentLength(d.bytes().length)
                .body(d.bytes());
    }

    @DeleteMapping("/files/{fileId}")
    @PreAuthorize("@documentAccess.canPublish()")
    public ResponseEntity<Void> delete(@PathVariable UUID fileId) {
        service.delete(fileId);
        return ResponseEntity.noContent().build();
    }
}
