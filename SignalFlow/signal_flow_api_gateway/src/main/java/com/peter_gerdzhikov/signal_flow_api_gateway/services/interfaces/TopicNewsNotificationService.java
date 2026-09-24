package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsEventDTO;

public interface TopicNewsNotificationService {

    void notifySubscribers(TopicNewsEventDTO event);
}
