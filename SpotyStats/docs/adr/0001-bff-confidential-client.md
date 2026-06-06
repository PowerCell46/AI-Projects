# Backend-for-Frontend with a confidential OAuth client

The spec asks for both "PKCE" and "tokens stored on the server, never in local storage." PKCE alone targets *public* clients that cannot keep a secret, which contradicts server-side token storage. We resolved this by making the Spring backend a **confidential OAuth client** that runs the *entire* Authorization Code flow (Backend-for-Frontend pattern): the browser never receives a Spotify token, only an HTTP-only, Secure session cookie, and all Spotify API calls are proxied through the backend. PKCE is retained as defense-in-depth on the server-side flow (per OAuth 2.1 / RFC 9700), not as the core mechanism.

## Implementation note
Realised with Spring Security's built-in OAuth2 Client (`oauth2Login`) rather than a hand-rolled flow: Spring performs the authorization-code exchange, PKCE, and token refresh. PKCE is opted-in for our confidential client via `OAuth2AuthorizationRequestCustomizers.withPkce()`, and `show_dialog=true` is added through the same authorization-request resolver. Tokens are persisted by a custom `EncryptedJdbcOAuth2AuthorizedClientService` (refresh token encrypted at rest via `Encryptors.delux`); the browser holds only the opaque Spring Session cookie (`SESSION`), backed by Postgres.

## Considered Options
- **BFF / confidential client (chosen)** — most secure, satisfies "tokens on server" cleanly.
- **SPA drives PKCE, backend stores tokens** — the authorization code lands in the browser first, weakening the "no token in browser" guarantee.
- **Pure SPA PKCE** — rejected outright; tokens would live in the browser, violating the spec.
