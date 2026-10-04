package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.TokenService;

@Service
public class TokenServiceImpl implements TokenService {

    private final Duration ttl;

    private final JwtEncoder jwtEncoder;

    public TokenServiceImpl(@Value("${app.jwt.ttl}") Duration ttl, JwtEncoder jwtEncoder) {
        this.ttl = ttl;
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * Stamps {@code iat}/{@code exp} from the system clock, not the injected {@code Clock}: the decoder
     * validates {@code exp} against the system clock, so a token minted against a moved test clock would
     * come out already expired.
     */
    @Override
    public String mint(User user) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
