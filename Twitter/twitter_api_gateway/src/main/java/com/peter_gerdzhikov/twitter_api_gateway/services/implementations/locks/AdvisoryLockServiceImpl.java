package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.locks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.exceptions.locks.AdvisoryLockFailedException;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockKey;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.locks.AdvisoryLockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Takes the lock on a connection of its own and keeps that connection until the action ends, because a Postgres
 * session-level advisory lock belongs to the connection, not to a transaction. Postgres frees it when the connection
 * closes, so a crashed instance never leaves it held.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdvisoryLockServiceImpl implements AdvisoryLockService {

    private static final String TRY_LOCK_SQL = "SELECT pg_try_advisory_lock(?)";

    private static final String UNLOCK_SQL = "SELECT pg_advisory_unlock(?)";

    private final DataSource dataSource;

    @Override
    public <T> Optional<T> runIfUnlocked(AdvisoryLockKey key, Supplier<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tryLock(connection, key)) {
                return Optional.empty();
            }

            try {
                return Optional.ofNullable(action.get());

            } finally {
                unlockOrDiscard(connection, key);
            }

        } catch (SQLException ex) {
            throw new AdvisoryLockFailedException("Could not take the advisory lock " + key + ".", ex);
        }
    }

    private boolean tryLock(Connection connection, AdvisoryLockKey key) throws SQLException {
        return queryBoolean(connection, TRY_LOCK_SQL, key);
    }

    // The pool would hand a connection that still holds the lock to the next caller, so one that cannot release it is dropped
    private void unlockOrDiscard(Connection connection, AdvisoryLockKey key) throws SQLException {
        try {
            queryBoolean(connection, UNLOCK_SQL, key);

        } catch (SQLException ex) {
            log.warn("Could not release the advisory lock {}; dropping its connection.", key, ex);
            connection.abort(Runnable::run);
        }
    }

    private boolean queryBoolean(Connection connection, String sql, AdvisoryLockKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, key.getId());

            try (ResultSet result = statement.executeQuery()) {
                result.next();

                return result.getBoolean(1);
            }
        }
    }
}
