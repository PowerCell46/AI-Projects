package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks;

import java.util.Optional;
import java.util.function.Supplier;

public interface AdvisoryLockService {

    /**
     * Runs the action only if no other instance holds the lock, and holds it until the action ends.
     *
     * @return the action's result, or empty when another instance holds the lock and the action did not run
     */
    <T> Optional<T> runIfUnlocked(AdvisoryLockKey key, Supplier<T> action);
}
