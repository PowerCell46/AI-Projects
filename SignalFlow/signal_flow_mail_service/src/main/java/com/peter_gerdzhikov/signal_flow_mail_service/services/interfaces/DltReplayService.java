package com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces;

public interface DltReplayService {

    /**
     * Republishes {@code topic-news.notification-requested-dlt} records to
     * {@code topic-news.notification-requested}, bounded by an offset snapshot taken at the start of the
     * run. Permanent failures are skipped by default; see {@code app.dlt-replay.include-permanent}.
     */
    void replay();
}
