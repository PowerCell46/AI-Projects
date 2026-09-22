package com.peter_gerdzhikov.signal_flow_interest_topic_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SignalFlowInterestTopicServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(SignalFlowInterestTopicServiceApplication.class, args);
	}
}
