package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces;

import java.time.LocalDate;
import java.util.List;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

public interface NewsGenerationService {

    String generate(InterestTopic interestTopic, LocalDate newsDate, LocalDate periodStart, List<String> coveredHeadlines);
}
