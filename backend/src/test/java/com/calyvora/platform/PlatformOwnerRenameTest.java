package com.calyvora.platform;

import com.calyvora.identity.Role;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.identity.UserStatus;
import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Changing the platform owner's address on a deployment that already has one.
 *
 * <p>This is the path no other test reaches: every test starts from an empty database, so the
 * bootstrap always takes its "create" branch. The live deployment is the opposite — it has an owner
 * under the old address, and the reconciliation only ever runs there. Which is exactly the shape of
 * the V40 incident: logic whose only real exercise was production.
 */
class PlatformOwnerRenameTest extends IntegrationTestBase {

    @Autowired
    private PlatformOwnerBootstrap bootstrap;

    @Autowired
    private UserRepository users;

    @Autowired
    private com.calyvora.company.CompanyRepository companies;

    private static final String LEGACY_EMAIL = "ownerorbit@calyvora.in";

    @Test
    @DisplayName("an owner on the old address is moved, not duplicated")
    void renames_rather_than_creating_a_second_owner() {
        // Start from the world as the deployment actually is: the platform company exists and its
        // owner sits on the previous address.
        UUID platformId = companies.findFirstByPlatformTrue().orElseThrow().getId();
        users.findByEmail(PLATFORM_OWNER_EMAIL).ifPresent(u ->
                users.renamePlatformOwner(u.getId(), LEGACY_EMAIL, "irrelevant-hash"));
        assertThat(users.findByEmail(LEGACY_EMAIL)).isPresent();

        bootstrap.ensurePlatformOwner();

        // Moved: the new address works, the old one is gone, and there is still exactly one OWNER.
        assertThat(users.findByEmail(PLATFORM_OWNER_EMAIL)).isPresent();
        assertThat(users.findByEmail(LEGACY_EMAIL)).isEmpty();
        assertThat(users.findByCompanyIdOrderByCreatedAtAsc(platformId).stream()
                .filter(u -> u.getRole() == Role.OWNER).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("the moved account can sign in on the new address with the configured password")
    void the_renamed_owner_can_actually_log_in() throws Exception {
        users.findByEmail(PLATFORM_OWNER_EMAIL).ifPresent(u ->
                users.renamePlatformOwner(u.getId(), LEGACY_EMAIL, "a-hash-nobody-knows"));

        bootstrap.ensurePlatformOwner();

        // The password is reset during the move, because an address that just changed has no
        // meaningful old password — and there is no reset flow to recover with if this were wrong.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", PLATFORM_OWNER_EMAIL,
                                "password", PLATFORM_OWNER_PASSWORD))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", LEGACY_EMAIL, "password", PLATFORM_OWNER_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("running it again changes nothing")
    void is_idempotent() {
        bootstrap.ensurePlatformOwner();
        bootstrap.ensurePlatformOwner();

        UUID platformId = companies.findFirstByPlatformTrue().orElseThrow().getId();
        assertThat(users.findByCompanyIdOrderByCreatedAtAsc(platformId).stream()
                .filter(u -> u.getRole() == Role.OWNER).count()).isEqualTo(1);
        // And no second company claiming to be the platform.
        assertThat(companies.findAll().stream().filter(com.calyvora.company.Company::isPlatform).count())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a company ADMIN is never mistaken for the platform owner")
    void only_moves_an_owner() {
        UUID platformId = companies.findFirstByPlatformTrue().orElseThrow().getId();
        // Someone else inside the platform company, with a lesser role. The reconciliation looks for
        // OWNER specifically; matching on "first user in the platform company" would hand this
        // account the vendor's address and password.
        User helper = new User(UUID.randomUUID(), platformId, "helper@calyvora.in",
                "Help", "Er", Role.ADMIN, UserStatus.ACTIVE);
        helper.setPasswordHash("hash");
        helper.setEmailVerifiedAt(Instant.now());
        users.save(helper);

        users.findByEmail(PLATFORM_OWNER_EMAIL).ifPresent(u ->
                users.renamePlatformOwner(u.getId(), LEGACY_EMAIL, "hash"));
        bootstrap.ensurePlatformOwner();

        assertThat(users.findByEmail("helper@calyvora.in")).isPresent();
        assertThat(users.findByEmail(PLATFORM_OWNER_EMAIL)).isPresent();
    }
}
