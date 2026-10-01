package com.calyvora.common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

    @Test
    void raw_tokens_are_unique_and_url_safe() {
        String a = TokenGenerator.rawToken();
        String b = TokenGenerator.rawToken();
        assertThat(a).isNotEqualTo(b);
        assertThat(a).matches("[A-Za-z0-9_-]+"); // url-safe, no padding
    }

    @Test
    void sha256_is_deterministic_64_hex_chars_and_hides_the_raw_token() {
        String raw = TokenGenerator.rawToken();
        String hash = TokenGenerator.sha256(raw);
        assertThat(hash).hasSize(64).matches("[0-9a-f]+");
        assertThat(TokenGenerator.sha256(raw)).isEqualTo(hash);
        assertThat(hash).isNotEqualTo(raw);
    }
}
