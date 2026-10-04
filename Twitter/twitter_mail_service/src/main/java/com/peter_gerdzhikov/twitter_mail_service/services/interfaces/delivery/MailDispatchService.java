package com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.MailClaimHeldException;

public interface MailDispatchService {

    /**
     * Claims the inbox key, sends and marks sent - or releases the claim and rethrows on a failure to send. An
     * already-sent key sends nothing.
     *
     * @throws MailClaimHeldException when another attempt already holds the inbox claim - retryable
     */
    void dispatchOnce(OutgoingMail mail);
}
