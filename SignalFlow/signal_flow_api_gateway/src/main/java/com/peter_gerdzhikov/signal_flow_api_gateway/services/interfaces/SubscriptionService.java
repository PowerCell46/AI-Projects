package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import java.util.List;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;

public interface SubscriptionService {

    List<Subscription> findAllForUser(UUID userId);

    Subscription subscribe(UUID userId, UUID interestTopicId);

    void unsubscribe(UUID userId, UUID interestTopicId);
}
