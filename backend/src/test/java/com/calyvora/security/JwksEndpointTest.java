package com.calyvora.security;

import com.calyvora.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The JWKS endpoint (SD-5) is public and exposes exactly the RS256 verification keys — public
 * material only, in the RFC 7517 shape peers need to verify Calyvora access tokens.
 */
class JwksEndpointTest extends IntegrationTestBase {

    @Test
    void jwks_is_public_and_exposes_a_signing_key() throws Exception {
        MvcResult result = mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andReturn();

        JsonNode key = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("keys").get(0);
        // Public key parameters present; no private material ever leaks here.
        org.assertj.core.api.Assertions.assertThat(key.hasNonNull("kid")).isTrue();
        org.assertj.core.api.Assertions.assertThat(key.hasNonNull("n")).isTrue();
        org.assertj.core.api.Assertions.assertThat(key.hasNonNull("e")).isTrue();
        org.assertj.core.api.Assertions.assertThat(key.has("d")).isFalse();
    }
}
