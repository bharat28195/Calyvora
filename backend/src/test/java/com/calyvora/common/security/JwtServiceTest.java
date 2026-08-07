package com.calyvora.common.security;

import com.calyvora.common.config.AppProperties;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RS256 signing, verification, and key rotation (SD-5). Pure unit tests — no Spring context —
 * driven off explicitly constructed keypairs so rotation can be simulated deterministically.
 */
class JwtServiceTest {

    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            UUID.randomUUID(), UUID.randomUUID(), "OWNER", "owner@calyvora.local");

    @Test
    void signs_and_verifies_round_trip() {
        JwtService service = serviceWith(activeKey("k1"));

        String token = service.createAccessToken(PRINCIPAL);
        AuthPrincipal parsed = service.parse(token);

        assertThat(parsed.userId()).isEqualTo(PRINCIPAL.userId());
        assertThat(parsed.companyId()).isEqualTo(PRINCIPAL.companyId());
        assertThat(parsed.role()).isEqualTo("OWNER");
        assertThat(parsed.email()).isEqualTo("owner@calyvora.local");
    }

    @Test
    void ephemeral_keypair_is_generated_when_no_keys_configured() {
        AppProperties p = props(jwt(null, null));
        JwtService service = new JwtService(new JwtKeyStore(p), p);

        String token = service.createAccessToken(PRINCIPAL);
        assertThat(service.parse(token).userId()).isEqualTo(PRINCIPAL.userId());
    }

    @Test
    void unfilled_key_slots_are_ignored_and_fall_back_to_ephemeral() {
        // application.yml declares fixed key slots fed by env vars; with those env vars unset the
        // slots bind as all-blank entries. Local dev must still boot, not fail as "misconfigured".
        AppProperties.RsaKey blank = new AppProperties.RsaKey("", "", "");
        AppProperties p = props(jwt("", List.of(blank, blank)));
        JwtService service = new JwtService(new JwtKeyStore(p), p);

        assertThat(service.parse(service.createAccessToken(PRINCIPAL)).userId()).isEqualTo(PRINCIPAL.userId());
    }

    @Test
    void partially_filled_key_slot_is_reported_but_does_not_stop_startup() {
        // A kid with no public key is a genuine misconfiguration, not an unused slot — but still
        // not a reason to refuse to boot.
        AppProperties p = props(jwt("k1", List.of(new AppProperties.RsaKey("k1", null, null))));

        JwtKeyStore store = new JwtKeyStore(p);

        assertThat(store.isEphemeral()).isTrue();
        assertThat(store.configurationError()).contains("k1");
    }

    @Test
    void single_line_pem_is_accepted() {
        // PEMs get pasted into a host's env-var UI as one line; the armor strip must cope.
        KeyPair pair = generate();
        AppProperties.RsaKey oneLine = new AppProperties.RsaKey("k1",
                singleLinePem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                singleLinePem("PUBLIC KEY", pair.getPublic().getEncoded()));
        AppProperties p = props(jwt("k1", List.of(oneLine)));
        JwtService service = new JwtService(new JwtKeyStore(p), p);

        assertThat(service.parse(service.createAccessToken(PRINCIPAL)).email()).isEqualTo("owner@calyvora.local");
    }

    @Test
    void swapped_public_and_private_keys_degrade_instead_of_killing_startup() {
        // The real-world slip: the private key pasted into the public-key variable. It must not
        // take the deployment down — a mistyped key should cost token persistence, not uptime.
        KeyPair pair = generate();
        AppProperties.RsaKey swapped = new AppProperties.RsaKey("k1",
                pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                pem("PRIVATE KEY", pair.getPrivate().getEncoded()));  // private in the public slot
        AppProperties p = props(jwt("k1", List.of(swapped)));

        JwtKeyStore store = new JwtKeyStore(p);

        assertThat(store.isEphemeral()).isTrue();
        assertThat(store.configurationError()).contains("k1").contains("swapped");
        // And the app is fully usable on the generated key.
        JwtService service = new JwtService(store, p);
        assertThat(service.parse(service.createAccessToken(PRINCIPAL)).userId()).isEqualTo(PRINCIPAL.userId());
    }

    @Test
    void valid_keys_are_not_reported_as_ephemeral() {
        JwtKeyStore store = new JwtKeyStore(props(jwt("k1", List.of(activeKey("k1")))));

        assertThat(store.isEphemeral()).isFalse();
        assertThat(store.configurationError()).isNull();
        assertThat(store.activeKid()).isEqualTo("k1");
    }

    @Test
    void pem_surrounded_by_quotes_is_accepted() {
        // Some dashboards keep the quotes when a value is pasted as a quoted string.
        KeyPair pair = generate();
        AppProperties.RsaKey quoted = new AppProperties.RsaKey("k1",
                "\"" + pem("PRIVATE KEY", pair.getPrivate().getEncoded()) + "\"",
                "\"" + pem("PUBLIC KEY", pair.getPublic().getEncoded()) + "\"");
        AppProperties p = props(jwt("k1", List.of(quoted)));
        JwtService service = new JwtService(new JwtKeyStore(p), p);

        assertThat(service.parse(service.createAccessToken(PRINCIPAL)).userId()).isEqualTo(PRINCIPAL.userId());
    }

    @Test
    void truncated_pem_names_the_key_it_came_from() {
        AppProperties.RsaKey truncated = new AppProperties.RsaKey(
                "orbit-key", null, "-----BEGIN PUBLIC KEY-----MIIBIjANBg-----END PUBLIC KEY-----");
        AppProperties p = props(jwt("orbit-key", List.of(truncated)));

        JwtKeyStore store = new JwtKeyStore(p);

        assertThat(store.isEphemeral()).isTrue();
        assertThat(store.configurationError()).contains("orbit-key");
    }

    @Test
    void token_signed_by_retired_key_still_verifies_after_rotation() {
        KeyPair k1 = generate();
        KeyPair k2 = generate();

        // Old world: k1 is active and signs a token.
        JwtService before = serviceWith("k1", keyOf("k1", k1, true));
        String tokenFromK1 = before.createAccessToken(PRINCIPAL);

        // After rotation: k2 becomes active, but k1 is retained (verify-only) in the trust set.
        AppProperties afterProps = props(jwt("k2", List.of(
                keyOf("k1", k1, false),   // retired: public only
                keyOf("k2", k2, true))));
        JwtService after = new JwtService(new JwtKeyStore(afterProps), afterProps);

        // The still-valid token minted under the old key remains verifiable — zero-downtime rotation.
        assertThat(after.parse(tokenFromK1).userId()).isEqualTo(PRINCIPAL.userId());

        // And new tokens are signed under k2 and verify too.
        String tokenFromK2 = after.createAccessToken(PRINCIPAL);
        assertThat(after.parse(tokenFromK2).email()).isEqualTo("owner@calyvora.local");
    }

    @Test
    void token_from_untrusted_key_is_rejected() {
        JwtService issuer = serviceWith(activeKey("k1"));
        String token = issuer.createAccessToken(PRINCIPAL);

        // A verifier that never heard of k1 must reject the token.
        JwtService verifier = serviceWith(activeKey("other"));
        assertThatThrownBy(() -> verifier.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tampered_token_is_rejected() {
        JwtService service = serviceWith(activeKey("k1"));
        String token = service.createAccessToken(PRINCIPAL);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void active_kid_without_private_key_degrades_instead_of_failing() {
        KeyPair k1 = generate();
        AppProperties p = props(jwt("k1", List.of(keyOf("k1", k1, false))));

        JwtKeyStore store = new JwtKeyStore(p);

        assertThat(store.isEphemeral()).isTrue();
        assertThat(store.configurationError()).contains("k1");
    }

    // --- helpers ---------------------------------------------------------------

    private static JwtService serviceWith(AppProperties.RsaKey... keys) {
        return serviceWith(keys[0].kid(), keys);
    }

    private static JwtService serviceWith(String activeKid, AppProperties.RsaKey... keys) {
        AppProperties p = props(jwt(activeKid, List.of(keys)));
        return new JwtService(new JwtKeyStore(p), p);
    }

    private static AppProperties.RsaKey activeKey(String kid) {
        return keyOf(kid, generate(), true);
    }

    private static AppProperties.RsaKey keyOf(String kid, KeyPair pair, boolean withPrivate) {
        String privatePem = withPrivate ? pem("PRIVATE KEY", pair.getPrivate().getEncoded()) : null;
        return new AppProperties.RsaKey(kid, privatePem, pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    }

    private static AppProperties.Jwt jwt(String activeKid, List<AppProperties.RsaKey> keys) {
        return new AppProperties.Jwt(activeKid, keys, Duration.ofMinutes(15), "calyvora");
    }

    private static AppProperties props(AppProperties.Jwt jwt) {
        return new AppProperties(null, null, null,
                new AppProperties.Security(jwt, null, null, null));
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String singleLinePem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----"
                + Base64.getEncoder().encodeToString(der)
                + "-----END " + label + "-----";
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder().encodeToString(der)
                + "\n-----END " + label + "-----";
    }
}
