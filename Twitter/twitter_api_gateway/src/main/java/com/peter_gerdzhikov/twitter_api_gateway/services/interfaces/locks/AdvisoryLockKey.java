package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Each value is one Postgres advisory lock shared by every gateway instance on the same database. A new value needs
 * a number no other value uses.
 */
@Getter
@RequiredArgsConstructor
public enum AdvisoryLockKey {

    UNCONFIRMED_USER_CLEANUP(1),

    DATA_SEED(2);

    private final long id;
}
