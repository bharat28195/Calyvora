package com.calyvora.common.security;

import com.calyvora.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds the RSA keys the {@link JwtService} signs and verifies with (SD-5, RS256 + rotation).
 *
 * <p>Exactly one key is <em>active</em> (used to sign new access tokens); every configured key —
 * plus retired verify-only keys — is trusted for verification, keyed by its {@code kid}. This split
 * is what makes zero-downtime rotation possible: publish the new public key, flip the active kid,
 * then drop the old key once no unexpired token could still bear it.
 *
 * <p>If no keys are configured a 2048-bit keypair is generated in memory at startup. That keeps
 * local/dev/test zero-config, but a restart invalidates every outstanding token, so a shared
 * environment MUST supply real PEM keys (and a warning is logged when it doesn't).
 */
@Component
public class JwtKeyStore {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyStore.class);

    private static final Pattern BEGIN_MARKER = Pattern.compile("-----BEGIN ([A-Z0-9 ]+)-----");
    private static final Pattern PEM_MARKER = Pattern.compile("-----(?:BEGIN|END) [A-Z0-9 ]+-----");

    private final String activeKid;
    private final PrivateKey signingKey;
    /** kid -> public key, insertion-ordered so JWKS output is stable. */
    private final Map<String, RSAPublicKey> verificationKeys;
    /** Why the configured keys were rejected, or {@code null} if they loaded (or none were given). */
    private final String configurationError;
    private final boolean ephemeral;

    public JwtKeyStore(AppProperties props) {
        AppProperties.Jwt jwt = props.security().jwt();
        Map<String, RSAPublicKey> publics = new LinkedHashMap<>();
        Map<String, PrivateKey> privates = new LinkedHashMap<>();
        String resolvedKid = null;
        String error = null;

        // application.yml declares fixed key slots fed by env vars, so an unused slot binds as an
        // all-blank entry. Drop those: "slot left empty" means no key, not a broken key. A slot with
        // *some* fields filled is a real mistake and is reported below.
        List<AppProperties.RsaKey> configured = jwt.keys() == null ? List.of()
                : jwt.keys().stream().filter(k -> !isEmptySlot(k)).toList();

        if (!configured.isEmpty()) {
            try {
                resolvedKid = load(jwt, configured, publics, privates);
            } catch (RuntimeException ex) {
                // Deliberately non-fatal. A mistyped key is a config error, and refusing to boot
                // turns it into a total outage — every user locked out to protect token longevity.
                // The fallback is a freshly generated 2048-bit keypair, so this costs persistence
                // across restarts, not strength. Logged at ERROR because it must not pass unnoticed.
                publics.clear();
                privates.clear();
                resolvedKid = null;
                error = ex.getMessage();
                log.error("RS256 JWT key configuration is invalid, so the app is running on an "
                        + "EPHEMERAL keypair — everyone is logged out on each restart until this is "
                        + "fixed. Cause: {}", ex.getMessage(), ex);
            }
        }

        boolean wasGenerated = resolvedKid == null;
        if (wasGenerated) {
            KeyPair generated = generateKeyPair();
            resolvedKid = "dev-" + UUID.randomUUID();
            publics.put(resolvedKid, (RSAPublicKey) generated.getPublic());
            privates.put(resolvedKid, generated.getPrivate());
            if (configured.isEmpty()) {
                log.warn("No RS256 JWT keys configured — generated an EPHEMERAL keypair (kid={}). "
                        + "All access tokens become invalid on restart. Configure "
                        + "calyvora.security.jwt.keys in any shared environment.", resolvedKid);
            }
        }

        this.activeKid = resolvedKid;
        this.signingKey = privates.get(resolvedKid);
        this.verificationKeys = Map.copyOf(publics);
        this.configurationError = error;
        this.ephemeral = wasGenerated;
        log.info("RS256 JWT key store ready: activeKid={}, {} verification key(s), ephemeral={}",
                activeKid, verificationKeys.size(), ephemeral);
    }

    /**
     * Parse every configured key and pick the active one. Throws on any problem — the caller turns
     * that into an ephemeral fallback rather than a failed startup.
     *
     * @return the active kid, guaranteed to have a private key to sign with.
     */
    private static String load(AppProperties.Jwt jwt, List<AppProperties.RsaKey> configured,
                               Map<String, RSAPublicKey> publics, Map<String, PrivateKey> privates) {
        for (AppProperties.RsaKey k : configured) {
            if (isBlank(k.publicKeyPem())) {
                throw new IllegalStateException("JWT key '" + k.kid() + "' is missing a public key");
            }
            publics.put(k.kid(), parsePublicKey(k.kid(), k.publicKeyPem()));
            if (!isBlank(k.privateKeyPem())) {
                privates.put(k.kid(), parsePrivateKey(k.kid(), k.privateKeyPem()));
            }
        }
        String kid = resolveActiveKid(jwt, privates);
        if (privates.get(kid) == null) {
            throw new IllegalStateException("Active JWT key '" + kid + "' has no private key to sign with");
        }
        return kid;
    }

    private static String resolveActiveKid(AppProperties.Jwt jwt, Map<String, PrivateKey> privates) {
        String configured = jwt.activeKid();
        if (configured != null && !configured.isBlank()) {
            if (!privates.containsKey(configured)) {
                throw new IllegalStateException(
                        "calyvora.security.jwt.active-kid='" + configured + "' has no matching signable key");
            }
            return configured;
        }
        // No explicit active kid: fall back to the first key that can sign.
        return privates.keySet().stream().findFirst().orElseThrow(() ->
                new IllegalStateException("No signable JWT key configured (every key is verify-only)"));
    }

    public String activeKid() {
        return activeKid;
    }

    /** Signing on a keypair generated at startup — tokens will not survive a restart. */
    public boolean isEphemeral() {
        return ephemeral;
    }

    /** Why configured keys were rejected, or {@code null} if they loaded / none were supplied. */
    public String configurationError() {
        return configurationError;
    }

    public PrivateKey signingKey() {
        return signingKey;
    }

    /** @return the public key for {@code kid}, or {@code null} if unknown (verification then fails). */
    public RSAPublicKey verificationKey(String kid) {
        return kid == null ? null : verificationKeys.get(kid);
    }

    /** kid -> public key, in a stable order — the source for the JWKS endpoint. */
    public Map<String, RSAPublicKey> verificationKeys() {
        return verificationKeys;
    }

    /** An entirely unfilled key slot — every field blank. */
    private static boolean isEmptySlot(AppProperties.RsaKey k) {
        return isBlank(k.kid()) && isBlank(k.privateKeyPem()) && isBlank(k.publicKeyPem());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate RSA keypair", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String kid, String pem) {
        try {
            byte[] der = decodePem(pem, "PUBLIC KEY");
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            return (RSAPublicKey) key;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key for kid '" + kid + "' (expect an "
                    + "X.509 'PUBLIC KEY' PEM): " + e.getMessage(), e);
        }
    }

    private static PrivateKey parsePrivateKey(String kid, String pem) {
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key for kid '" + kid + "' (expect a "
                    + "PKCS#8 'PRIVATE KEY' PEM): " + e.getMessage(), e);
        }
    }

    /**
     * Strip the {@code -----BEGIN/END <label>-----} armor and any whitespace, then Base64-decode.
     *
     * <p>These values are typed or pasted into a hosting dashboard by hand, so the armor is matched
     * by pattern rather than exact text: a mismatch used to leave the dashes in place and surface as
     * "Illegal base64 character 2d", which says nothing about the actual mistake. When the label is
     * present but wrong — overwhelmingly a private key pasted into the public-key variable, or the
     * two swapped — say exactly that instead.
     */
    private static byte[] decodePem(String pem, String expectedLabel) {
        String cleaned = pem.trim();
        // Some dashboards keep the surrounding quotes when a value is pasted as a quoted string.
        if (cleaned.length() > 1 && (cleaned.startsWith("\"") && cleaned.endsWith("\"")
                || cleaned.startsWith("'") && cleaned.endsWith("'"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        Matcher begin = BEGIN_MARKER.matcher(cleaned);
        if (begin.find()) {
            String found = begin.group(1).trim();
            if (!found.equals(expectedLabel)) {
                throw new IllegalStateException("expected a '" + expectedLabel + "' PEM but the value "
                        + "is a '" + found + "' PEM — the public and private keys look swapped");
            }
        }

        String body = PEM_MARKER.matcher(cleaned).replaceAll("").replaceAll("\\s", "");
        if (body.isEmpty()) {
            throw new IllegalStateException("PEM body is empty once the armor is stripped");
        }
        return Base64.getDecoder().decode(body);
    }
}
