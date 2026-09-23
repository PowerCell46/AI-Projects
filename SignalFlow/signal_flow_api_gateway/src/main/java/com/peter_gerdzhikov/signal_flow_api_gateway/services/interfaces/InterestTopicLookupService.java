package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface InterestTopicLookupService {

    Set<UUID> findExistingIds(Collection<UUID> interestTopicIds);
}
