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
// Self-signup is closed in production (PD-21) — a workspace now comes from a trial request the vendor
// approves. It stays switched on here because {@link #onboardOwner} is how nearly every test in this
// suite conjures a tenant, and rewriting all of them to provision through the owner console would be
// a large change that tests the console rather than the feature each one is about. The production
// default is asserted directly, and hard, in TrialRequestFlowTest.
@SpringBootTest(properties = "calyvora.security.registration.open=true")
// The rate limiter is off across the suite: every test logs in, often many times, always from the
// same address, so a limit sized for a stranger guessing passwords would fail tests that are
// behaving correctly. RateLimitIntegrationTest switches it back on and is the one place it is
// exercised.
//
// This is a @TestPropertySource rather than another entry in @SpringBootTest above, and deliberately
// so: a subclass that re-declares @SpringBootTest replaces the properties there wholesale — which
// TrialRequestFlowTest does on purpose — and would silently switch the limiter back on and fail for
// reasons that have nothing to do with what it is testing. @TestPropertySource is inherited and
// merged instead, so a subclass has to mean it.
// The platform-owner password has no built-in default any more (it must come from the environment on a
// real deployment), so the test context supplies its own throwaway value here. It is inherited and
// merged by every subclass, including those that re-declare @SpringBootTest.
@org.springframework.test.context.TestPropertySource(properties = {
        "calyvora.rate-limit.enabled=false",
        "calyvora.platform.owner-password=Test-Platform-Owner-1!"})
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = ZONKY, refresh = AFTER_EACH_TEST_METHOD)
@Import({RecordingEmailService.class, RlsRoleConfig.class})
public abstract class IntegrationTestBase {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected RecordingEmailService email;

    @Autowired
    protected com.calyvora.platform.PlatformOwnerBootstrap platformOwner;

    /** The platform vendor. Real infrastructure, not demo data — created at startup (PD-18). */
    protected static final String PLATFORM_OWNER_EMAIL = "bharat28195@calyvora.in";
    // Test-only; matches calyvora.platform.owner-password in @TestPropertySource above. Not a real
    // credential — the production password lives only in the PLATFORM_OWNER_PASSWORD env var.
    protected static final String PLATFORM_OWNER_PASSWORD = "Test-Platform-Owner-1!";

    @BeforeEach
    void clearEmail() {
        email.clear();
    }

    /**
     * The owner is created once when the context starts, but this suite refreshes the database
     * between test methods — which deletes it. Re-establishing it here means every test can sign in
     * as the vendor without each one knowing why it would otherwise be missing.
     */
    @BeforeEach
    void ensurePlatformOwner() {
        platformOwner.ensurePlatformOwner();
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

    private String platformBearerCache;

    /**
     * A bearer header for the platform owner, cached per test.
     *
     * <p>The scale-seed and mail-test dev endpoints are restricted to the vendor — an anonymous
     * caller on the live deployment could otherwise pollute the running tenant or send mail on the
     * vendor's account — so the tests that build the scale tenant as setup present this.
     */
    protected String platformBearer() throws Exception {
        if (platformBearerCache == null) {
            platformBearerCache = "Bearer " + login(PLATFORM_OWNER_EMAIL, PLATFORM_OWNER_PASSWORD).accessToken();
        }
        return platformBearerCache;
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

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate testJdbc;

    /**
     * Switch the absent-after-grace rules off for this session's company.
     *
     * <p>A new company has them on from the day it is created (V70), so on a weekday after shift start
     * plus grace everyone who has not checked in is ABSENT. A test about something else — a payslip, a
     * bank file, a dashboard headcount — then passes in the morning and fails in the afternoon. Tests
     * that assume nobody is absent call this to say so; attendance tests set the date themselves.
     */
    protected void attendanceRulesOff(Session session) throws Exception {
        // Reading the settings creates the row if the company has none yet, so the update has one to change.
        getJson("/api/v1/company/settings", session);
        asTenant(session, "update company_settings set attendance_rules_from = null where company_id = ?");
    }

    /**
     * Run one statement against the session's own company, its id bound as the LAST parameter after
     * {@code args}. Tables are under FORCE row level security, so the tenant is bound on the same
     * connection and reset afterwards. For setting up what no endpoint can, such as a date in the past.
     */
    protected int asTenant(Session session, String sql, Object... args) throws Exception {
        String payload = session.accessToken().split("[.]")[1];
        JsonNode claims = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(payload));
        String companyId = claims.get("companyId").asText();
        return testJdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Integer>) c -> {
            try (var set = c.prepareStatement("select set_config('calyvora.company_id', ?, false)")) {
                set.setString(1, companyId);
                set.execute();
            }
            try (var st = c.prepareStatement(sql)) {
                for (int i = 0; i < args.length; i++) st.setObject(i + 1, args[i]);
                st.setObject(args.length + 1, java.util.UUID.fromString(companyId));
                return st.executeUpdate();
            } finally {
                try (var reset = c.prepareStatement("select set_config('calyvora.company_id', '', false)")) {
                    reset.execute();
                }
            }
        });
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
