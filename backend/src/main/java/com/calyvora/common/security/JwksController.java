package com.calyvora.common.security;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Publishes the RS256 verification keys as a JWKS document (SD-5) at the conventional
 * {@code /.well-known/jwks.json}. Any peer — the frontend, a future service, an API gateway — can
 * fetch this to verify Calyvora access tokens without sharing a secret, and rotation is transparent:
 * a new key appears here before the first token is signed with it, and a retired key lingers until
 * its last token expires. Public keys only; nothing here is sensitive.
 */
@RestController
public class JwksController {

    private final JwtKeyStore keyStore;

    public JwksController(JwtKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        List<Map<String, Object>> keys = new ArrayList<>();
        keyStore.verificationKeys().forEach((kid, key) -> keys.add(toJwk(kid, key)));
        return Map.of("keys", keys);
    }

    private static Map<String, Object> toJwk(String kid, RSAPublicKey key) {
        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", kid);
        jwk.put("n", base64UrlUint(key.getModulus()));
        jwk.put("e", base64UrlUint(key.getPublicExponent()));
        return jwk;
    }

    /** Base64url-encode a positive integer as a minimal unsigned big-endian byte string (RFC 7518 §2). */
    private static String base64UrlUint(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // BigInteger prepends a 0x00 sign byte when the high bit is set — strip it for the unsigned form.
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
