package com.calyvora.platform;

import com.calyvora.common.util.Slugs;
import com.calyvora.company.Company;
import com.calyvora.company.CompanyRepository;
import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.company.CompanyStatus;
import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Ensures the platform-owner account exists at startup, in every profile.
 *
 * <p>It used to be created by {@code DemoSeedService}, which is dev-only and disabled in prod — so the
 * one account that can see every tenant depended on someone remembering to call a seeding endpoint,
 * and carried a demo password. It is infrastructure, not demo data, so it is created here instead:
 * idempotent, safe to run on every boot, and configurable per environment.
 *
 * <p>The password is taken from {@code PLATFORM_OWNER_PASSWORD} when set. The built-in default exists
 * so a fresh deployment is usable immediately, and the app says loudly when it is in use — a shared
 * default on the account that reads every customer's data is worth being noisy about.
 */
@Component
public class PlatformOwnerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformOwnerBootstrap.class);
    private static final String PLATFORM_COMPANY = "Calyvora (Platform)";

    /**
     * The founder's own account, so a fresh deployment is usable without setting anything.
     *
     * <p><b>This password is in the source tree, so it is not a secret.</b> Anyone who can read the
     * repository can sign in as the account that sees every customer on the platform. Set
     * {@code PLATFORM_OWNER_PASSWORD} on any deployment holding real customer data; the app says so
     * loudly at startup while this default is in use.
     */
    private static final String DEFAULT_PASSWORD = "Bharat@28195#";

    private final CompanyRepository companyRepository;
    private final CompanySettingsRepository settingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String ownerEmail;
    private final String ownerPassword;
    private final boolean usingDefaultPassword;

    public PlatformOwnerBootstrap(CompanyRepository companyRepository,
                                  CompanySettingsRepository settingsRepository,
                                  UserRepository userRepository,
                                  PasswordEncoder passwordEncoder,
                                  @Value("${calyvora.platform.owner-email:bharat28195@calyvora.in}") String ownerEmail,
                                  @Value("${calyvora.platform.owner-password:}") String configuredPassword) {
        this.companyRepository = companyRepository;
        this.settingsRepository = settingsRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.ownerEmail = ownerEmail.trim().toLowerCase();
        this.usingDefaultPassword = configuredPassword == null || configuredPassword.isBlank();
        this.ownerPassword = usingDefaultPassword ? DEFAULT_PASSWORD : configuredPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensurePlatformOwner();
    }

    /**
     * Create the platform company and owner if they are missing. Separate from {@link #run} so it can
     * be called directly — integration tests reset the database between methods, which would otherwise
     * delete an account that only ever gets created once, at context startup.
     *
     * @return true when this call created the owner
     */
    @Transactional
    public boolean ensurePlatformOwner() {
        if (userRepository.existsByEmail(ownerEmail)) {
            return false;
        }
        if (renameExistingOwner()) {
            return false;
        }

        // Reuse the platform company if one survived (V40 only drops it when it has no members left),
        // so re-running this never leaves two companies claiming to be the platform.
        Company platform = companyRepository.findFirstByPlatformTrue().orElseGet(() -> {
            Company created = new Company(UUID.randomUUID(), PLATFORM_COMPANY,
                    uniqueSlug(PLATFORM_COMPANY), CompanyStatus.ACTIVE);
            created.setPlatform(true);
            Company saved = companyRepository.save(created);
            settingsRepository.save(new CompanySettings(saved.getId()));
            return saved;
        });

        User owner = new User(UUID.randomUUID(), platform.getId(), ownerEmail,
                "Orbit", "Owner", Role.OWNER, UserStatus.ACTIVE);
        owner.setPasswordHash(passwordEncoder.encode(ownerPassword));
        owner.setEmailVerifiedAt(Instant.now());
        userRepository.save(owner);

        log.info("Created the platform owner {}.", ownerEmail);
        if (usingDefaultPassword) {
            log.warn("The platform owner is using the built-in default password. This account can read "
                    + "every customer on the platform — set PLATFORM_OWNER_PASSWORD and restart.");
        }
        return true;
    }

    /**
     * Move an existing platform owner onto the configured address instead of creating a second one.
     *
     * <p>Changing {@code calyvora.platform.owner-email} on a deployment that already has an owner
     * would otherwise leave two accounts able to read every customer — the old one still live, still
     * holding its old password, and invisible in any console that shows customer companies.
     *
     * <p>This renames rather than deleting and recreating, which is the V40 lesson applied: the owner
     * row is referenced from refresh tokens and a dozen {@code created_by} columns, so an UPDATE
     * always succeeds where a DELETE meets foreign keys and fails the deploy.
     *
     * <p>The password is reset at the same time, because an account whose address just changed has
     * no meaningful "existing password" to preserve — and being locked out of the owner console with
     * no reset flow in the product would be unrecoverable without database access.
     *
     * @return true when a legacy owner was found and moved
     */
    private boolean renameExistingOwner() {
        Company platform = companyRepository.findFirstByPlatformTrue().orElse(null);
        if (platform == null) {
            return false;
        }
        User legacy = userRepository.findByCompanyIdOrderByCreatedAtAsc(platform.getId()).stream()
                .filter(u -> u.getRole() == Role.OWNER)
                .findFirst()
                .orElse(null);
        if (legacy == null) {
            return false;
        }

        String previous = legacy.getEmail();
        if (previous.equalsIgnoreCase(ownerEmail)) {
            return true;   // already where it should be; existsByEmail simply hadn't matched on case
        }
        userRepository.renamePlatformOwner(legacy.getId(), ownerEmail, passwordEncoder.encode(ownerPassword));

        log.warn("Moved the platform owner from {} to {} and reset its password. The old address can "
                + "no longer sign in.", previous, ownerEmail);
        return true;
    }

    private String uniqueSlug(String name) {
        String base = Slugs.slugify(name);
        String slug = base;
        while (companyRepository.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }
}
