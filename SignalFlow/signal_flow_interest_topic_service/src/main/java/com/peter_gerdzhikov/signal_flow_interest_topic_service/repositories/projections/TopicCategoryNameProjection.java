package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.projections;

import java.util.UUID;

public interface TopicCategoryNameProjection {

    UUID getTopicId();

    String getCategoryName();
}
