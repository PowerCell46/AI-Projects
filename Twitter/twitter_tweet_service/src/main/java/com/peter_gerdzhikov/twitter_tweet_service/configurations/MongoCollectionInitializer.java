package com.peter_gerdzhikov.twitter_tweet_service.configurations;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates the collections before the web server starts taking requests. Mongo creates a missing collection on
 * its first write, and when several transactions do that at once all but one fail with a write conflict, so the
 * first concurrent tweets on a fresh database would answer 500.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoCollectionInitializer implements SmartInitializingSingleton {

    private final MongoTemplate mongoTemplate;

    @Override
    public void afterSingletonsInstantiated() {
        createIfMissing(Tweet.class);
        createIfMissing(Reply.class);
        createIfMissing(OutboxMessage.class);
    }

    private void createIfMissing(Class<?> documentType) {
        if (mongoTemplate.collectionExists(documentType)) {
            return;
        }

        try {
            mongoTemplate.createCollection(documentType);
            log.info("Created Mongo collection for {}.", documentType.getSimpleName());

        } catch (DataAccessException e) {
            // Another instance starting at the same moment can create it first; that is the outcome wanted.
            if (!mongoTemplate.collectionExists(documentType)) {
                throw e;
            }
        }
    }
}
