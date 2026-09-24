package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

public interface InboxCleanupService {

    void deleteProcessedNewsOlderThanRetention();
}
