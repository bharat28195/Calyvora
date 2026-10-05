package com.calyvora.knowledge.storage;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.knowledge.CompanyFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Bytes in a Cloudflare R2 bucket, over the S3 API. Active only when all four R2 settings are present
 * (see {@link FileStorageRouter}).
 *
 * <p>Objects are keyed {@code <companyId>/<fileId>}: the company is part of the key, so even a bug that
 * mixed up two file ids could not hand one company another's object.
 */
public class R2FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(R2FileStorage.class);
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final String host;
    private final String bucket;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public R2FileStorage(String accountId, String bucket, String accessKeyId, String secretAccessKey) {
        this.host = accountId + ".r2.cloudflarestorage.com";
        this.bucket = bucket;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    @Override
    public String kind() {
        return "R2";
    }

    @Override
    public String put(CompanyFile file, byte[] bytes) {
        String key = file.getCompanyId() + "/" + file.getId();
        send("PUT", key, bytes, file.getContentType());
        return key;
    }

    @Override
    public byte[] get(CompanyFile file) {
        return send("GET", file.getStorageKey(), null, null);
    }

    @Override
    public void delete(CompanyFile file) {
        send("DELETE", file.getStorageKey(), null, null);
    }

    private byte[] send(String method, String key, byte[] body, String contentType) {
        String path = "/" + bucket + "/" + key;   // keys are UUIDs and '/', nothing to encode
        String payloadHash = body == null ? S3Signer.EMPTY_SHA256 : S3Signer.sha256Hex(body);
        String amzDate = ZonedDateTime.now(ZoneOffset.UTC).format(AMZ_DATE);
        String auth = S3Signer.authorization(method, host, path,
                Map.of("x-amz-date", amzDate, "x-amz-content-sha256", payloadHash),
                payloadHash, amzDate, "auto", accessKeyId, secretAccessKey);

        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("https://" + host + path))
                .timeout(Duration.ofSeconds(60))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", payloadHash)
                .header("Authorization", auth);
        if (contentType != null) {
            req.header("Content-Type", contentType);
        }
        req.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<byte[]> res = http.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() / 100 != 2) {
                log.warn("R2 {} {} answered {}: {}", method, key, res.statusCode(),
                        new String(res.body(), java.nio.charset.StandardCharsets.UTF_8));
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "File storage refused the request");
            }
            return res.body();
        } catch (java.io.IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "File storage could not be reached");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "File storage request was interrupted");
        }
    }
}
