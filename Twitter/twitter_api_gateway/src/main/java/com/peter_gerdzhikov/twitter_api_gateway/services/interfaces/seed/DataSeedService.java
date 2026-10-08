package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed;

public interface DataSeedService {

    /**
     * Creates the demo users, their follows and their tweets, but only when no user exists yet.
     * Returns true when it seeded, false when it left the database alone.
     */
    boolean seedIfEmpty();
}
