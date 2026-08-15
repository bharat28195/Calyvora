package com.calyvora.auth;

import com.calyvora.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forgotten passwords, by one-time code (PD-23).
 *
 * <p>This is a second front door into every account, so most of what is asserted here is what the
 * flow <em>refuses</em> to do: leak whether an address exists, accept a code twice, let a guesser
 * keep guessing, or leave old sessions alive after the password behind them has changed.
 */
class PasswordResetIntegrationTest extends IntegrationTestBase {

    private static final String EMAIL = "dana@resetco.test";
    private static final String OLD_PASSWORD = "OldPassw0rd!";
    private static final String NEW_PASSWORD = "BrandNewPass9";

    @Autowired
    private com.calyvora.identity.UserRepository users;

    private void onboard() throws Exception {
        onboardOwner("Reset Co", EMAIL, OLD_PASSWORD);
        email().clear();   // drop the verification mail so lastResetCode() is unambiguous
    }

    private void forgot(String address) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", address))))
                .andExpect(status().isAccepted());
    }

    private org.springframework.test.web.servlet.ResultActions reset(String address, String code,
                                                                    String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", address, "code", code, "newPassword", password))));
    }

    // ---- the happy path ----

    @Test
    @DisplayName("a code arrives, sets a new password, and the new password works")
    void the_whole_flow() throws Exception {
        onboard();
        forgot(EMAIL);

        assertThat(email().resetCodes()).hasSize(1);
        String code = email().lastResetCode();
        assertThat(code).matches("\\d{6}");

        reset(EMAIL, code, NEW_PASSWORD).andExpect(status().isNoContent());

        login(EMAIL, NEW_PASSWORD);   // throws if it fails
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", OLD_PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    // ---- what it refuses to give away ----

    @Test
    @DisplayName("an unknown address is answered exactly like a known one")
    void does_not_reveal_whether_an_account_exists() throws Exception {
        onboard();

        forgot("nobody@nowhere.test");
        // Same 202 as the real address above — and, crucially, nothing was sent.
        assertThat(email().resetCodes()).isEmpty();

        forgot(EMAIL);
        assertThat(email().resetCodes()).hasSize(1);
    }

    @Test
    @DisplayName("a wrong code and a non-existent account fail identically")
    void failures_are_indistinguishable() throws Exception {
        onboard();
        forgot(EMAIL);

        String wrongCodeMessage = messageOf(reset(EMAIL, "000000", NEW_PASSWORD)
                .andExpect(status().isBadRequest()));
        String noAccountMessage = messageOf(reset("nobody@nowhere.test", "000000", NEW_PASSWORD)
                .andExpect(status().isBadRequest()));

        assertThat(wrongCodeMessage).isEqualTo(noAccountMessage);
    }

    // ---- what it refuses to do ----

    @Test
    @DisplayName("a code works once")
    void a_code_cannot_be_replayed() throws Exception {
        onboard();
        forgot(EMAIL);
        String code = email().lastResetCode();

        reset(EMAIL, code, NEW_PASSWORD).andExpect(status().isNoContent());
        // Someone reading the mailbox later must not be able to reuse it.
        reset(EMAIL, code, "SomethingElse123").andExpect(status().isBadRequest());

        login(EMAIL, NEW_PASSWORD);
    }

    @Test
    @DisplayName("asking again kills the previous code")
    void a_new_code_invalidates_the_old_one() throws Exception {
        onboard();
        forgot(EMAIL);
        String first = email().lastResetCode();
        forgot(EMAIL);
        String second = email().lastResetCode();
        assertThat(second).isNotEqualTo(first);

        // Two live codes would double a guesser's odds for no benefit to anyone.
        reset(EMAIL, first, NEW_PASSWORD).andExpect(status().isBadRequest());
        reset(EMAIL, second, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("guessing is capped at five tries, not a million")
    void wrong_guesses_burn_the_code() throws Exception {
        onboard();
        forgot(EMAIL);
        String code = email().lastResetCode();

        for (int i = 0; i < PasswordResetCode.MAX_ATTEMPTS; i++) {
            // Deliberately never the real code — "000000" would collide 1 time in a million.
            reset(EMAIL, "1" + String.format("%05d", i), NEW_PASSWORD)
                    .andExpect(status().isBadRequest());
        }

        // The real code is now dead too. That is the point: six digits is only safe because the
        // number of tries is small, so exhausting them has to end the code, not just that attempt.
        reset(EMAIL, code, NEW_PASSWORD).andExpect(status().isBadRequest());
        login(EMAIL, OLD_PASSWORD);
    }

    @Test
    @DisplayName("one address cannot be mail-bombed through this endpoint")
    void requests_are_throttled() throws Exception {
        onboard();
        for (int i = 0; i < PasswordResetService.MAX_REQUESTS_PER_HOUR + 3; i++) {
            forgot(EMAIL);   // every one answers 202; the caller learns nothing
        }
        assertThat(email().resetCodes()).hasSize(PasswordResetService.MAX_REQUESTS_PER_HOUR);
    }

    @Test
    @DisplayName("resetting signs every other session out")
    void existing_sessions_are_revoked() throws Exception {
        onboard();
        Session before = login(EMAIL, OLD_PASSWORD);

        forgot(EMAIL);
        reset(EMAIL, email().lastResetCode(), NEW_PASSWORD).andExpect(status().isNoContent());

        // The likeliest reason to reset is that a session is somewhere it shouldn't be. If the old
        // refresh token still worked, the new password would change nothing for whoever holds it.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("calyvora_rt", before.refreshToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the new password has to be a real password")
    void the_new_password_is_validated() throws Exception {
        onboard();
        forgot(EMAIL);
        String code = email().lastResetCode();

        // This endpoint is a second front door; if it accepted "abc" then that is what the account's
        // password rules actually are, whatever the signup screen says.
        reset(EMAIL, code, "short").andExpect(status().isBadRequest());
        reset(EMAIL, code, "alllettersnodigits").andExpect(status().isBadRequest());

        // Validation failures are not guesses, so they must not have burned the code.
        reset(EMAIL, code, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a disabled account cannot be revived by whoever holds the mailbox")
    void disabled_accounts_get_nothing() throws Exception {
        onboard();
        var user = users.findByEmail(EMAIL).orElseThrow();
        user.setStatus(com.calyvora.identity.UserStatus.DISABLED);
        users.save(user);

        forgot(EMAIL);
        assertThat(email().resetCodes()).isEmpty();
    }

    @Test
    @DisplayName("the payload still has to be a payload")
    void the_request_is_validated() throws Exception {
        Map<String, String> notAnEmail = new HashMap<>();
        notAnEmail.put("email", "not-an-address");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON).content(json(notAnEmail)))
                .andExpect(status().isBadRequest());

        reset(EMAIL, "12345", NEW_PASSWORD).andExpect(status().isBadRequest());    // 5 digits
        reset(EMAIL, "abcdef", NEW_PASSWORD).andExpect(status().isBadRequest());   // not digits
    }

    private String messageOf(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString())
                .get("message").asText();
    }
}
