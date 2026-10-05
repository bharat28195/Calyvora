package com.calyvora.knowledge.storage;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/**
 * AWS Signature Version 4 for S3-compatible storage (Cloudflare R2 speaks the S3 API).
 *
 * <p>Hand-written rather than the AWS SDK, for the same reason the assistant calls Claude over the
 * JDK client: the build stays offline-safe and the deployable small, and three object operations do
 * not need a forty-module SDK. It is short enough to read, and {@code S3SignerTest} pins it to the
 * worked example in AWS's own SigV4 documentation, so a mistake here fails a test rather than a
 * customer's upload.
 */
public final class S3Signer {

    public static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    private S3Signer() {
    }

    /**
     * @param headers every header to sign except {@code host}, names lower-case; must include
     *                {@code x-amz-date} and {@code x-amz-content-sha256}
     * @param canonicalPath the URL path, already URI-encoded per segment (S3 does not double-encode)
     * @return the value of the {@code Authorization} header
     */
    public static String authorization(String method, String host, String canonicalPath, Map<String, String> headers,
                                       String payloadSha256, String amzDate, String region,
                                       String accessKeyId, String secretAccessKey) {
        TreeMap<String, String> signed = new TreeMap<>(headers);
        signed.put("host", host);

        StringBuilder canonicalHeaders = new StringBuilder();
        for (Map.Entry<String, String> h : signed.entrySet()) {
            canonicalHeaders.append(h.getKey()).append(':').append(h.getValue().trim()).append('\n');
        }
        String signedHeaders = String.join(";", signed.keySet());

        String canonicalRequest = method + "\n" + canonicalPath + "\n" + "" + "\n"
                + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadSha256;

        String date = amzDate.substring(0, 8);
        String scope = date + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + sha256Hex(canonicalRequest);

        byte[] kDate = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, "s3");
        byte[] kSigning = hmac(kService, "aws4_request");
        String signature = HexFormat.of().formatHex(hmac(kSigning, stringToSign));

        return "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sha256Hex(String s) {
        return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
