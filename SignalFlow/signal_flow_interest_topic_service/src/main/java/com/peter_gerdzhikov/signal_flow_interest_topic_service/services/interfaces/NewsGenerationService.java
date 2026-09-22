package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

public interface NewsGenerationService {

    String generate(InterestTopic interestTopic);
}
