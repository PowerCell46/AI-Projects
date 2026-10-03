package com.peter_gerdzhikov.twitter_timeline_service.configurations.downstream;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DownstreamExecutorConfiguration {

    /**
     * Runs the calls of one request side by side. A call only waits on the network, so each gets its own
     * virtual thread; each is bounded by the HTTP read timeout, which bounds the request too.
     */
    @Bean
    public ExecutorService downstreamCallExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
