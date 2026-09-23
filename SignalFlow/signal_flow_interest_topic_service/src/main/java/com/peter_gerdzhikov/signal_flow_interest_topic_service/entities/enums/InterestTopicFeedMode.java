package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums;

/**
 * How a feed request's id set narrows the topics: ignored, kept as the only topics, or left out.
 */
public enum InterestTopicFeedMode {

    ALL,

    INCLUDE,

    EXCLUDE
}
