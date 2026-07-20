package com.calyvora.common.security;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Convenience meta-annotation: inject the current {@link AuthPrincipal} into a controller method.
 *
 * <pre>{@code
 * @GetMapping("/me")
 * MeResponse me(@CurrentUser AuthPrincipal principal) { ... }
 * }</pre>
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
public @interface CurrentUser {
}
