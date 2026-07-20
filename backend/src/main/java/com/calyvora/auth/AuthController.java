package com.calyvora.auth;

import com.calyvora.auth.dto.LoginRequest;
import com.calyvora.auth.dto.LoginResponse;
import com.calyvora.auth.dto.MeResponse;
import com.calyvora.auth.dto.RegisterRequest;
import com.calyvora.auth.dto.ResendVerificationRequest;
import com.calyvora.auth.dto.VerifyEmailRequest;
import com.calyvora.common.config.AppProperties;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/** Authentication + registration surface (Sprint1 §7). All endpoints here are public except /me. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AppProperties.Refresh refreshProps;

    public AuthController(AuthService authService, AppProperties props) {
        this.authService = authService;
        this.refreshProps = props.security().refresh();
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.login(
                request.email(), request.password(), httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString())
                .body(result.body());
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "${calyvora.security.refresh.cookie-name}", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        AuthService.LoginResult result = authService.refresh(
                refreshToken, httpRequest.getHeader(HttpHeaders.USER_AGENT));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(result.refreshToken()).toString())
                .body(result.body());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "${calyvora.security.refresh.cookie-name}", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public MeResponse me(@CurrentUser AuthPrincipal principal) {
        return authService.me(principal.userId());
    }

    // ---- refresh cookie helpers ----

    private ResponseCookie buildRefreshCookie(String value) {
        return ResponseCookie.from(refreshProps.cookieName(), value)
                .httpOnly(true)
                .secure(refreshProps.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(refreshProps.ttl())
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(refreshProps.cookieName(), "")
                .httpOnly(true)
                .secure(refreshProps.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }
}
