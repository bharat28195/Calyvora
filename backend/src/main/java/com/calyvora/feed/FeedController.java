package com.calyvora.feed;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.feed.dto.PostPayload;
import com.calyvora.feed.dto.PostResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The company feed. Everyone can post, react and comment; pinning is Owner/Admin. */
@RestController
@RequestMapping("/api/v1/feed")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping
    public List<PostResponse> feed(@CurrentUser AuthPrincipal principal) {
        return feedService.feed(principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@Valid @RequestBody PostPayload payload, @CurrentUser AuthPrincipal principal) {
        return feedService.create(payload, principal);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID postId, @CurrentUser AuthPrincipal principal) {
        feedService.delete(postId, principal);
    }

    @PostMapping("/{postId}/pin")
    public PostResponse pin(@PathVariable UUID postId, @RequestBody(required = false) Map<String, Boolean> body,
                            @CurrentUser AuthPrincipal principal) {
        boolean pinned = body == null || body.get("pinned") == null || body.get("pinned");
        return feedService.setPinned(postId, pinned, principal);
    }

    /** Toggles the reaction — sending the same emoji again removes it. */
    @PostMapping("/{postId}/react")
    public PostResponse react(@PathVariable UUID postId, @RequestBody(required = false) Map<String, String> body,
                              @CurrentUser AuthPrincipal principal) {
        return feedService.react(postId, body == null ? null : body.get("emoji"), principal);
    }

    @PostMapping("/{postId}/comments")
    public PostResponse comment(@PathVariable UUID postId, @RequestBody Map<String, String> body,
                                @CurrentUser AuthPrincipal principal) {
        return feedService.comment(postId, body.get("body"), principal);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID commentId, @CurrentUser AuthPrincipal principal) {
        feedService.deleteComment(commentId, principal);
    }
}
