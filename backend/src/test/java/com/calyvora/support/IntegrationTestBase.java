package com.calyvora.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY;
import static io.zonky.test.db.AutoConfigureEmbeddedDatabase.RefreshMode.AFTER_EACH_TEST_METHOD;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for full-stack integration tests. Boots the whole app against a <em>real</em> embedded
 * Postgres (Zonky — no Docker required); Flyway builds the schema fresh before each test method.
 * The recording email service captures verification/invite tokens.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = ZONKY, refresh = AFTER_EACH_TEST_METHOD)
@Import(RecordingEmailService.class)
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RecordingEmailService email;

    @BeforeEach
    void clearEmail() {
        email.clear();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** An authenticated session: bearer access token + refresh cookie value. */
    public record Session(String accessToken, String refreshToken) {
    }

    /**
     * Full onboarding: register an Owner, verify the emailed token, and log in.
     * @return the resulting authenticated session.
     */
    protected Session onboardOwner(String companyName, String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("companyName", companyName, "firstName", "Test",
                                "lastName", "Owner", "email", email, "password", password))))
                .andExpect(status().isCreated());

        String token = email().lastVerificationToken();
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("token", token))))
                .andExpect(status().isOk());

        return login(email, password);
    }

    protected Session login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String access = body.get("accessToken").asText();
        String refresh = refreshCookie(result.getResponse());
        return new Session(access, refresh);
    }

    protected JsonNode getJson(String path, Session session) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + session.accessToken()))
                .andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(content);
    }

    protected RecordingEmailService email() {
        return email;
    }

    private static String refreshCookie(MockHttpServletResponse response) {
        String cookie = response.getHeader("Set-Cookie");
        if (cookie == null) {
            return null;
        }
        // e.g. "calyvora_rt=<value>; Path=/; ..."
        int eq = cookie.indexOf('=');
        int semi = cookie.indexOf(';');
        return cookie.substring(eq + 1, semi < 0 ? cookie.length() : semi);
    }
}
