package com.wealthbuilder.backend.config;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;


/**
 * Builds the httpOnly cookie that carries the JWT. Centralised so the attributes (HttpOnly,
 * Secure, SameSite, Path, Max-Age) are identical when the cookie is issued at login and when it
 * is cleared at logout — a mismatch on any attribute would leave the browser holding a stale
 * cookie the clear never overwrites.
 */
@Component
public class AuthTokenCookie {

    private final AppProperties appProperties;

    public AuthTokenCookie(AppProperties appProperties) {
        this.appProperties = appProperties;
        ensureSameSiteSecurePairing();
        ensureSameSiteNotNone();
    }

    /** The cookie carrying the token, valid for the JWT's lifetime. */
    public ResponseCookie issue(String token) {
        return baseBuilder(token)
                .maxAge(appProperties.getJwt().getTtl())
                .build();
    }

    /** An immediately-expiring cookie of the same name/path, so logout drops the session. */
    public ResponseCookie clear() {
        return baseBuilder("")
                .maxAge(0)
                .build();
    }

    public String getName() {
        return appProperties.getAuth().getCookieName();
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder(String value) {
        final AppProperties.Auth auth = appProperties.getAuth();

        return ResponseCookie
                .from(auth.getCookieName(), value)
                .httpOnly(true)
                .secure(auth.isCookieSecure())
                .sameSite(auth.getCookieSameSite())
                .path("/");
    }

    /**
     * Fails fast if SameSite=None is paired with Secure=false. Browsers silently reject a
     * SameSite=None cookie that lacks the Secure flag — auth would be silently broken with no
     * server-side indication.
     */
    private void ensureSameSiteSecurePairing() {
        final AppProperties.Auth auth = appProperties.getAuth();

        if ("None".equalsIgnoreCase(auth.getCookieSameSite()) && !auth.isCookieSecure()) {
            throw new IllegalStateException(
                    "SameSite=None requires Secure=true — browsers reject a None cookie without the Secure flag "
                    + "and auth will silently break. Set AUTH_COOKIE_SECURE=true, or switch to SameSite=Lax "
                    + "for a same-site deployment.");
        }
    }

    /**
     * Fails fast if SameSite=None is configured. This application disables CSRF protection on the
     * assumption that SameSite=Lax prevents the browser from attaching the cookie to cross-site
     * state-changing requests. SameSite=None removes that defence: every mutation endpoint
     * (POST, PUT, DELETE) would be CSRF-vulnerable. Re-enable CSRF in SecurityConfig before
     * switching to None.
     */
    private void ensureSameSiteNotNone() {
        if ("None".equalsIgnoreCase(appProperties.getAuth().getCookieSameSite())) {
            throw new IllegalStateException(
                    "SameSite=None is not permitted: this application relies on SameSite=Lax as its CSRF defence. "
                    + "SameSite=None allows cross-site requests to carry the cookie, making every mutation endpoint "
                    + "CSRF-vulnerable while CSRF protection is disabled. "
                    + "Use SameSite=Lax for a same-site deployment behind a shared reverse proxy, "
                    + "or re-enable CSRF in SecurityConfig before setting SameSite=None.");
        }
    }
}
