package com.calyvora.email;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Local-dev mailbox endpoint ({@code embedded} profile only) so the frontend {@code /dev/mailbox}
 * page can show verification/invite links in live mode — no SMTP needed. Public (see SecurityConfig)
 * and never registered in prod, where this bean does not exist.
 */
@RestController
@RequestMapping("/api/v1/dev/mailbox")
@Profile("embedded")
public class DevMailboxController {

    private final DevMailbox mailbox;

    public DevMailboxController(DevMailbox mailbox) {
        this.mailbox = mailbox;
    }

    @GetMapping
    public List<DevMailMessage> list() {
        return mailbox.list();
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        mailbox.clear();
        return ResponseEntity.noContent().build();
    }
}
