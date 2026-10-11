package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.auth;

import java.time.Clock;
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

    private final Clock clock;

    public TokenServiceImpl(
            @Value("${app.jwt.ttl}") Duration ttl,
            JwtEncoder jwtEncoder,
            Clock clock
    ) {
        this.ttl = ttl;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    @Override
    public String mint(User user) {
        Instant issuedAt = Instant.now(clock);
        JwtClaimsSet claims = JwtClaimsSet
                .builder()
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
