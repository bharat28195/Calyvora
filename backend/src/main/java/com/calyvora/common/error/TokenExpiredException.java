package com.calyvora.common.error;

/** A single-use token (verification / invitation) is expired or already consumed → 410 Gone. */
public class TokenExpiredException extends ApiException {
    public TokenExpiredException(String message) {
        super(ErrorCode.TOKEN_EXPIRED, message);
    }
}
