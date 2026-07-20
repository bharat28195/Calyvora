package com.calyvora.knowledge;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.knowledge.dto.CreatePageRequest;
import com.calyvora.knowledge.dto.PageResponse;
import com.calyvora.knowledge.dto.PageSummary;
import com.calyvora.knowledge.dto.UpdatePageRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Knowledge OS — pages (slices K2–K5), including "my pages" and tenant-wide search. */
@RestController
@RequestMapping("/api/v1/knowledge")
public class PageController {

    private final PageService pageService;

    public PageController(PageService pageService) {
        this.pageService = pageService;
    }

    @GetMapping("/spaces/{spaceId}/pages")
    public List<PageSummary> listForSpace(@PathVariable UUID spaceId) {
        return pageService.listForSpace(spaceId);
    }

    @PostMapping("/spaces/{spaceId}/pages")
    public ResponseEntity<PageResponse> create(@PathVariable UUID spaceId,
                                               @Valid @RequestBody CreatePageRequest request,
                                               @CurrentUser AuthPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pageService.create(spaceId, request, principal));
    }

    @GetMapping("/pages/mine")
    public List<PageSummary> mine(@CurrentUser AuthPrincipal principal) {
        return pageService.mine(principal);
    }

    @GetMapping("/search")
    public List<PageSummary> search(@RequestParam("q") String q) {
        return pageService.search(q);
    }

    @GetMapping("/pages/{id}")
    public PageResponse get(@PathVariable UUID id) {
        return pageService.get(id);
    }

    @PatchMapping("/pages/{id}")
    public PageResponse update(@PathVariable UUID id, @Valid @RequestBody UpdatePageRequest request) {
        return pageService.update(id, request);
    }

    @DeleteMapping("/pages/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
