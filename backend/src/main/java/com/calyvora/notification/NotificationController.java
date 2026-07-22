package com.calyvora.notification;

import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import com.calyvora.notification.dto.NotificationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Your inbox (feedback D4/D5). Everything here is scoped to the signed-in user. */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                           @CurrentUser AuthPrincipal principal) {
        return notificationService.list(principal, unreadOnly);
    }

    /** Cheap poll for the header bell. */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@CurrentUser AuthPrincipal principal) {
        return Map.of("count", notificationService.unreadCount(principal));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable UUID id, @CurrentUser AuthPrincipal principal) {
        return notificationService.markRead(id, principal);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@CurrentUser AuthPrincipal principal) {
        return Map.of("marked", notificationService.markAllRead(principal));
    }
}
