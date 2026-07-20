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
import java.util.Map;
import java.util.UUID;

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

    private final String activeKid;
    private final PrivateKey signingKey;
    /** kid -> public key, insertion-ordered so JWKS output is stable. */
    private final Map<String, RSAPublicKey> verificationKeys;

    public JwtKeyStore(AppProperties props) {
        AppProperties.Jwt jwt = props.security().jwt();
        Map<String, RSAPublicKey> publics = new LinkedHashMap<>();
        Map<String, PrivateKey> privates = new LinkedHashMap<>();

        if (jwt.keys() == null || jwt.keys().isEmpty()) {
            KeyPair generated = generateKeyPair();
            String kid = "dev-" + UUID.randomUUID();
            publics.put(kid, (RSAPublicKey) generated.getPublic());
            privates.put(kid, generated.getPrivate());
            this.activeKid = kid;
            log.warn("No RS256 JWT keys configured — generated an EPHEMERAL keypair (kid={}). "
                    + "All access tokens become invalid on restart. Configure calyvora.security.jwt.keys "
                    + "in any shared environment.", kid);
        } else {
            for (AppProperties.RsaKey k : jwt.keys()) {
                if (k.publicKeyPem() == null || k.publicKeyPem().isBlank()) {
                    throw new IllegalStateException("JWT key '" + k.kid() + "' is missing a public key");
                }
                publics.put(k.kid(), parsePublicKey(k.publicKeyPem()));
                if (k.privateKeyPem() != null && !k.privateKeyPem().isBlank()) {
                    privates.put(k.kid(), parsePrivateKey(k.privateKeyPem()));
                }
            }
            this.activeKid = resolveActiveKid(jwt, privates);
        }

        this.signingKey = privates.get(activeKid);
        this.verificationKeys = Map.copyOf(publics);
        if (this.signingKey == null) {
            throw new IllegalStateException("Active JWT key '" + activeKid + "' has no private key to sign with");
        }
        log.info("RS256 JWT key store ready: activeKid={}, {} verification key(s)",
                activeKid, verificationKeys.size());
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

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            return gen.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate RSA keypair", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PUBLIC KEY");
            PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            return (RSAPublicKey) key;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key PEM (expect PKCS#8 'PUBLIC KEY')", e);
        }
    }

    private static PrivateKey parsePrivateKey(String pem) {
        try {
            byte[] der = decodePem(pem, "PRIVATE KEY");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key PEM (expect PKCS#8 'PRIVATE KEY')", e);
        }
    }

    /** Strip the {@code -----BEGIN/END <label>-----} armor and any whitespace, then Base64-decode. */
    private static byte[] decodePem(String pem, String label) {
        String body = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
