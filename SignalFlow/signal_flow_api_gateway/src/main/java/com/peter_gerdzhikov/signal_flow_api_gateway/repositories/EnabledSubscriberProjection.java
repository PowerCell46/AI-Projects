package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import java.util.UUID;

public interface EnabledSubscriberProjection {

    UUID getUserId();

    String getEmail();
}
