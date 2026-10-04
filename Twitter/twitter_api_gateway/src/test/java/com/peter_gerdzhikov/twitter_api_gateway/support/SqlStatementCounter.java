package com.peter_gerdzhikov.twitter_api_gateway.support;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import jakarta.persistence.EntityManagerFactory;

/**
 * Counts the SQL statements Hibernate prepares while an action runs. The count is the difference between two
 * reads, so statements from other tests don't matter. Needs {@code hibernate.generate_statistics}, which the
 * test profile switches on.
 */
public final class SqlStatementCounter {

    private SqlStatementCounter() {
    }

    public static long countDuring(EntityManagerFactory entityManagerFactory, Runnable action) {
        Statistics statistics = entityManagerFactory
                .unwrap(SessionFactory.class)
                .getStatistics();
        long before = statistics.getPrepareStatementCount();

        action.run();

        return statistics.getPrepareStatementCount() - before;
    }
}
