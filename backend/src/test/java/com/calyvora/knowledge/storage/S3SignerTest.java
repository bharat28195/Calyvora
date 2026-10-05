package com.calyvora.knowledge.storage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SigV4 signer against the worked example in AWS's documentation ("Signature Calculations for the
 * Authorization Header: Transferring Payload in a Single Chunk", example GET Object). Every value
 * below, including the expected signature, is copied from that page — if the signer drifts by one
 * byte anywhere in the canonical request, the signature changes and this fails.
 */
class S3SignerTest {

    @Test
    void matches_the_aws_documented_get_object_example() {
        String auth = S3Signer.authorization("GET", "examplebucket.s3.amazonaws.com", "/test.txt",
                Map.of("range", "bytes=0-9",
                        "x-amz-content-sha256", S3Signer.EMPTY_SHA256,
                        "x-amz-date", "20130524T000000Z"),
                S3Signer.EMPTY_SHA256, "20130524T000000Z", "us-east-1",
                "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");

        assertThat(auth).isEqualTo("AWS4-HMAC-SHA256 "
                + "Credential=AKIAIOSFODNN7EXAMPLE/20130524/us-east-1/s3/aws4_request, "
                + "SignedHeaders=host;range;x-amz-content-sha256;x-amz-date, "
                + "Signature=f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41");
    }

    @Test
    void hashes_an_empty_payload_to_the_well_known_value() {
        assertThat(S3Signer.sha256Hex(new byte[0])).isEqualTo(S3Signer.EMPTY_SHA256);
    }
}
