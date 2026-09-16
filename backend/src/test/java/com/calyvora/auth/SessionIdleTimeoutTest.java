package com.calyvora.auth;

import com.calyvora.company.CompanySettings;
import com.calyvora.company.CompanySettingsRepository;
import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The idle timeout, enforced where it counts.
 *
 * <p>A countdown in the browser is a courtesy, not a control: it can be closed, paused or edited.
 * What actually ends the session is the refresh cookie's lifetime being capped at the idle window,
 * which is what these assert.
 */
class SessionIdleTimeoutTest extends IntegrationTestBase {

    private static final String EMAIL = "admin@idleco.test";
    private static final String PW = "Passw0rd!x";

    @Autowired
    private CompanySettingsRepository settingsRepository;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    private Session onboard() throws Exception {
        return onboardOwner("Idle Co", EMAIL, PW);
    }

    private void setIdleMinutes(Session session, Integer minutes) throws Exception {
        mockMvc.perform(patch("/api/v1/company/settings")
                        .header("Authorization", "Bearer " + session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "Asia/Kolkata", "locale", "en", "currency", "INR",
                                "sessionIdleMinutes", minutes))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("with no policy a session lasts the full refresh lifetime")
    void no_policy_means_the_long_lifetime() throws Exception {
        Session owner = onboard();
        RefreshToken token = newestToken();
        // The configured default is fourteen days; anything of that order proves the cap did not apply.
        assertThat(Duration.between(Instant.now(), token.getExpiresAt())).isGreaterThan(Duration.ofDays(1));
        assertThat(owner.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("setting an idle window caps how long a session can sit untouched")
    void the_window_caps_the_cookie() throws Exception {
        Session owner = onboard();
        setIdleMinutes(owner, 15);

        // The cap applies from the next token issued — a refresh, which is what the app does while
        // somebody is using it.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("calyvora_rt", owner.refreshToken())))
                .andExpect(status().isOk());

        Duration life = Duration.between(Instant.now(), newestToken().getExpiresAt());
        assertThat(life).isLessThanOrEqualTo(Duration.ofMinutes(15));
        assertThat(life).isGreaterThan(Duration.ofMinutes(13));
    }

    @Test
    @DisplayName("a session left past the window cannot be renewed")
    void an_idle_session_is_over() throws Exception {
        Session owner = onboard();
        setIdleMinutes(owner, 15);

        String raw = refreshOnce(owner.refreshToken());

        // Fifteen minutes pass with nobody touching anything. Ageing the row is the same thing as
        // waiting, and the point is what the server does with the cookie afterwards.
        RefreshToken token = newestToken();
        token.setExpiresAtForTest(Instant.now().minusSeconds(1));
        refreshTokens.save(token);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("calyvora_rt", raw)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("turning the policy off with 0 restores the long lifetime")
    void zero_clears_the_policy() throws Exception {
        Session owner = onboard();
        setIdleMinutes(owner, 15);
        setIdleMinutes(owner, 0);

        assertThat(settingsRepository.findById(java.util.UUID.fromString(
                getJson("/api/v1/auth/me", owner).get("company").get("id").asText()))
                .orElseThrow().getSessionIdleMinutes()).isNull();

        refreshOnce(owner.refreshToken());
        assertThat(Duration.between(Instant.now(), newestToken().getExpiresAt()))
                .isGreaterThan(Duration.ofDays(1));
    }

    @Test
    @DisplayName("an idle window under five minutes is refused")
    void too_short_is_refused() throws Exception {
        Session owner = onboard();
        mockMvc.perform(patch("/api/v1/company/settings")
                        .header("Authorization", "Bearer " + owner.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "Asia/Kolkata", "locale", "en", "currency", "INR",
                                "sessionIdleMinutes", 2))))
                .andExpect(status().isBadRequest());
    }

    /** Rotate once and hand back the new raw token from the Set-Cookie header. */
    private String refreshOnce(String raw) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("calyvora_rt", raw)))
                .andExpect(status().isOk())
                .andReturn();
        var cookie = result.getResponse().getCookie("calyvora_rt");
        assertThat(cookie).isNotNull();
        return cookie.getValue();
    }

    private RefreshToken newestToken() {
        List<RefreshToken> all = refreshTokens.findAll();
        assertThat(all).isNotEmpty();
        return all.stream().max(java.util.Comparator.comparing(RefreshToken::getCreatedAt)).orElseThrow();
    }
}
