package com.peter_gerdzhikov.signal_flow_mail_service.runners;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.DltReplayService;

import lombok.RequiredArgsConstructor;

/**
 * Entry point for {@code --spring.profiles.active=dlt-replay}: runs one bounded replay pass, then shuts
 * the app down - this profile is a one-shot command, not a long-running service.
 */
@Component
@Profile("dlt-replay")
@RequiredArgsConstructor
public class DltReplayRunner implements CommandLineRunner {

    private final DltReplayService dltReplayService;

    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(String... args) {
        dltReplayService.replay();
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
