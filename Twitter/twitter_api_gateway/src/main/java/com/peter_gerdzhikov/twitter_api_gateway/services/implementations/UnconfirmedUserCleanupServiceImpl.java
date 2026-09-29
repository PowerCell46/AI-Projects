package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.UnconfirmedUserCleanupService;

@Service
public class UnconfirmedUserCleanupServiceImpl implements UnconfirmedUserCleanupService {

    private final Clock clock;

    private final Duration retention;

    private final UserRepository userRepository;

    public UnconfirmedUserCleanupServiceImpl(
            Clock clock,
            @Value("${app.users.unconfirmed-cleanup.retention}") Duration retention,
            UserRepository userRepository
    ) {
        this.clock = clock;
        this.retention = retention;
        this.userRepository = userRepository;
    }

    @Override
    public int deleteExpiredUnconfirmedUsers() {
        Instant cutoff = clock.instant().minus(retention);

        return userRepository.deleteUnconfirmedCreatedBefore(cutoff);
    }
}
