package com.calyvora.email;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the captured mailbox so the frontend {@code /dev/mailbox} page can show verification and
 * invite links with no mail provider configured. Public (see SecurityConfig), and registered in every
 * profile except {@code prod} — including staging, where a demo needs the link and real delivery is
 * usually not set up. See {@link DevMailbox} for why that is safe only outside prod.
 */
@RestController
@RequestMapping("/api/v1/dev/mailbox")
@Profile("!prod")
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
