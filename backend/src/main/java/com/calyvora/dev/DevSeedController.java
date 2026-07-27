package com.calyvora.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * One-call demo provisioning ({@code POST /api/v1/dev/seed-demo}) so a client demo opens onto a
 * populated, believable product. Public (under the {@code /api/v1/dev/**} surface) and disabled in
 * prod. Idempotent — safe to click twice; it returns the same login either way.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile("!prod")
public class DevSeedController {

    private final DemoSeedService demoSeedService;

    public DevSeedController(DemoSeedService demoSeedService) {
        this.demoSeedService = demoSeedService;
    }

    @PostMapping("/seed-demo")
    public DemoSeedService.DemoCredentials seed() {
        return demoSeedService.seed();
    }

    /** Provision 5 varied sample companies so the platform-owner console has a full picture. */
    @PostMapping("/seed-platform")
    public java.util.List<String> seedPlatform() {
        return demoSeedService.seedPlatformSamples();
    }
}
