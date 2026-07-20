package com.calyvora.auth.dto;

/** Body returned by login/refresh. The refresh token itself is delivered as an httpOnly cookie. */
public record LoginResponse(String accessToken, MeResponse me) {
}
