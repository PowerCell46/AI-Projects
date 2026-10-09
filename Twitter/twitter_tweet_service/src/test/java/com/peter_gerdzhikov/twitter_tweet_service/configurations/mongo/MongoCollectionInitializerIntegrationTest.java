package com.peter_gerdzhikov.twitter_tweet_service.configurations.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class MongoCollectionInitializerIntegrationTest extends AbstractMinioIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoCollectionInitializer mongoCollectionInitializer;

    @Nested
    class Startup {

        @Test
        void should_have_created_the_replies_collection_and_the_ix_replies_tweet_created_id_index_when_the_context_started() {
            assertTrue(mongoTemplate.collectionExists(Reply.class), "the replies collection must exist right after startup");
            assertThat(mongoTemplate.indexOps(Reply.class).getIndexInfo())
                    .filteredOn(index -> index.getName().equals("ix_replies_tweet_created_id"))
                    .singleElement()
                    .satisfies(index -> assertThat(index.getIndexFields())
                            .extracting(field -> field.getKey())
                            .containsExactly("tweetId", "createdAt", "_id"));
        }

        @Test
        void should_have_created_both_collections_when_the_context_started() {
            assertTrue(mongoTemplate.collectionExists(Tweet.class), "the tweets collection must exist right after startup");
            assertTrue(mongoTemplate.collectionExists(OutboxMessage.class), "the outbox collection must exist right after startup");
        }
    }

    @Nested
    class AfterSingletonsInstantiated {

        @Test
        void should_keep_the_collections_and_not_fail_when_they_already_exist() {
            assertDoesNotThrow(mongoCollectionInitializer::afterSingletonsInstantiated);

            assertTrue(mongoTemplate.collectionExists(Tweet.class), "the tweets collection must still exist after a second run");
            assertTrue(mongoTemplate.collectionExists(OutboxMessage.class), "the outbox collection must still exist after a second run");
        }

        @Test
        void should_recreate_the_collections_when_they_are_missing() {
            mongoTemplate.dropCollection(Tweet.class);
            mongoTemplate.dropCollection(OutboxMessage.class);

            mongoCollectionInitializer.afterSingletonsInstantiated();

            assertTrue(mongoTemplate.collectionExists(Tweet.class), "the initializer must create a missing tweets collection");
            assertTrue(mongoTemplate.collectionExists(OutboxMessage.class), "the initializer must create a missing outbox collection");
        }
    }
}
