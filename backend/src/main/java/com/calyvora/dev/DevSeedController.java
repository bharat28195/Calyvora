package com.calyvora.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * Everything at once, over GET, so a demo can be prepared by pasting a URL into a browser:
     * the Northwind company and the five sample companies that fill the owner console.
     *
     * <p>GET for a write is a deliberate exception to the usual rule. The justification is that
     * seeding is idempotent and purely additive — it only ever fills gaps, never overwrites or
     * deletes — so the harm a stray prefetch or a double-click can do is nil. Making it POST would
     * mean it could not be reached from the address bar, which is the entire point.
     *
     * <p>Still {@code @Profile("!prod")} and still under the public {@code /api/v1/dev/**} surface:
     * anyone who can reach the deployment can call this, which is why it must never exist on a
     * deployment holding real customer data.
     */
    @GetMapping("/seed-all")
    public SeedAllResult seedAll() {
        DemoSeedService.DemoCredentials demo = demoSeedService.seed();
        java.util.List<String> samples = demoSeedService.seedPlatformSamples();
        return new SeedAllResult(true, demo, samples);
    }

    /** @param companies the sample companies now visible in the platform-owner console */
    public record SeedAllResult(boolean seeded, DemoSeedService.DemoCredentials demo,
                                java.util.List<String> companies) {}
}
