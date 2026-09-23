package com.peter_gerdzhikov.signal_flow_api_gateway.configurations;

import static com.peter_gerdzhikov.signal_flow_api_gateway.configurations.InterestTopicRoutesConfiguration.CATEGORIES_PATH;
import static com.peter_gerdzhikov.signal_flow_api_gateway.configurations.InterestTopicRoutesConfiguration.INTEREST_TOPICS_PATH;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.utilities.CookieBearerTokenResolver;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final AccessDeniedHandler accessDeniedHandler;

    private final AuthenticationEntryPoint authenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter
    ) throws Exception {
        http
                // Same-origin SPA + SameSite=Strict already stops the browser attaching the cookie to a
                // cross-site request, which is exactly what CSRF protection defends against.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, CATEGORIES_PATH, INTEREST_TOPICS_PATH)
                        .authenticated()
                        // Every other method, not just the known writes, so a verb added downstream later is closed by default.
                        .requestMatchers(CATEGORIES_PATH, INTEREST_TOPICS_PATH)
                        .hasRole(Role.ADMIN.name())
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${app.security.bcrypt-strength}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    public SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(requiredClaimsValidator());

        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);

        return converter;
    }

    private OAuth2TokenValidator<Jwt> requiredClaimsValidator() {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                JwtValidators.createDefault(),
                new JwtClaimValidator<String>("sub", this::isUserId),
                new JwtClaimValidator<String>("email", Objects::nonNull),
                new JwtClaimValidator<String>("role", Objects::nonNull),
                new JwtClaimValidator<>("exp", Objects::nonNull))
        );
    }

    /**
     * Rejected here rather than at the controllers that parse the subject, so an unparsable id fails
     * as a clean 401 instead of an {@code IllegalArgumentException} past the decoder.
     */
    private boolean isUserId(String subject) {
        if (subject == null) {
            return false;
        }

        try {
            UUID.fromString(subject);
            return true;

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
