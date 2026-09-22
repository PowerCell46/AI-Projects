package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.TokenService;

@Service
public class TokenServiceImpl implements TokenService {

    private final Duration ttl;

    private final JwtEncoder jwtEncoder;

    public TokenServiceImpl(@Value("${app.jwt.ttl}") Duration ttl, JwtEncoder jwtEncoder) {
        this.ttl = ttl;
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    public String mint(User user) {
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .claim("createdAt", user.getCreatedAt().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttl))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
