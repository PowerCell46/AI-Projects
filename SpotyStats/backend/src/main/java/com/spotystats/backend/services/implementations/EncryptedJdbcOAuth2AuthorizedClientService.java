package com.spotystats.backend.services.implementations;

import com.spotystats.backend.utilities.OAuth2ScopeFormatter;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;


/**
 * Persists OAuth2 authorized clients (Spotify tokens) in Postgres, keyed to the
 * principal. The long-lived refresh token is encrypted at rest; the short-lived
 * access token is stored as-is.
 *
 * <p>Used both by the login filter (to save tokens after the code exchange) and by
 * the {@code OAuth2AuthorizedClientManager} (to load tokens and drive refresh).
 */
@Service
public class EncryptedJdbcOAuth2AuthorizedClientService implements OAuth2AuthorizedClientService {

    private static final String UPSERT_SQL = """
            insert into spotify_authorized_client (
                client_registration_id, principal_name, access_token_type, access_token_value,
                access_token_issued_at, access_token_expires_at, access_token_scopes,
                refresh_token_value, refresh_token_issued_at, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            on conflict (client_registration_id, principal_name) do update set
                access_token_type = excluded.access_token_type,
                access_token_value = excluded.access_token_value,
                access_token_issued_at = excluded.access_token_issued_at,
                access_token_expires_at = excluded.access_token_expires_at,
                access_token_scopes = excluded.access_token_scopes,
                refresh_token_value = excluded.refresh_token_value,
                refresh_token_issued_at = excluded.refresh_token_issued_at
            """;

    private static final String SELECT_SQL = """
            select access_token_type, access_token_value, access_token_issued_at,
                   access_token_expires_at, access_token_scopes, refresh_token_value,
                   refresh_token_issued_at
            from spotify_authorized_client
            where client_registration_id = ? and principal_name = ?
            """;

    private static final String DELETE_SQL = """
            delete from spotify_authorized_client
            where client_registration_id = ? and principal_name = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final TextEncryptor refreshTokenEncryptor;

    public EncryptedJdbcOAuth2AuthorizedClientService(
            JdbcTemplate jdbcTemplate,
            ClientRegistrationRepository clientRegistrationRepository,
            TextEncryptor refreshTokenEncryptor) {

        this.jdbcTemplate = jdbcTemplate;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.refreshTokenEncryptor = refreshTokenEncryptor;
    }

    @Override
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(
            String clientRegistrationId, String principalName) {

        final ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId(clientRegistrationId);

        if (registration == null) {
            return null;
        }

        try {
            return (T) jdbcTemplate.queryForObject(
                    SELECT_SQL,
                    rowMapper(registration, principalName),
                    clientRegistrationId,
                    principalName);

        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    @Override
    @Transactional
    public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal) {
        final OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        final OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        jdbcTemplate.update(
                UPSERT_SQL,
                authorizedClient.getClientRegistration().getRegistrationId(),
                authorizedClient.getPrincipalName(),
                accessToken.getTokenType().getValue(),
                accessToken.getTokenValue(),
                toOffset(accessToken.getIssuedAt()),
                toOffset(accessToken.getExpiresAt()),
                OAuth2ScopeFormatter.joinScopes(accessToken.getScopes()),
                encryptRefreshToken(refreshToken),
                refreshToken != null ? toOffset(refreshToken.getIssuedAt()) : null);
    }

    @Override
    @Transactional
    public void removeAuthorizedClient(String clientRegistrationId, String principalName) {
        jdbcTemplate.update(DELETE_SQL, clientRegistrationId, principalName);
    }

    private RowMapper<OAuth2AuthorizedClient> rowMapper(ClientRegistration registration, String principalName) {
        return (rs, rowNum) -> {
            final OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    rs.getString("access_token_value"),
                    toInstant(rs.getObject("access_token_issued_at", OffsetDateTime.class)),
                    toInstant(rs.getObject("access_token_expires_at", OffsetDateTime.class)),
                    OAuth2ScopeFormatter.splitScopes(rs.getString("access_token_scopes")));

            final String encryptedRefresh = rs.getString("refresh_token_value");
            final OAuth2RefreshToken refreshToken = encryptedRefresh == null
                    ? null
                    : new OAuth2RefreshToken(
                            refreshTokenEncryptor.decrypt(encryptedRefresh),
                            toInstant(rs.getObject("refresh_token_issued_at", OffsetDateTime.class)));

            return new OAuth2AuthorizedClient(registration, principalName, accessToken, refreshToken);
        };
    }

    private String encryptRefreshToken(OAuth2RefreshToken refreshToken) {
        if (refreshToken == null) {
            return null;
        }

        return refreshTokenEncryptor.encrypt(refreshToken.getTokenValue());
    }

    private static OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }
}
