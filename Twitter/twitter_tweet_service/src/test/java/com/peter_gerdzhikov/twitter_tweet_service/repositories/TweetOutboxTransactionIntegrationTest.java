package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.peter_gerdzhikov.twitter_tweet_service.configurations.MongoConfiguration;
import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMongoIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

/**
 * Proves the transaction manager is really wired to Mongo: the tweet and its outbox message are one unit.
 */
@DataMongoTest
@Import(MongoConfiguration.class)
class TweetOutboxTransactionIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private TweetRepository tweetRepository;

    @Autowired
    private MongoTransactionManager transactionManager;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Test
    void should_store_both_documents_when_the_transaction_commits() {
        Tweet tweet = TestDocuments.tweet();
        OutboxMessage message = TestDocuments.outboxMessage(OutboxStatus.PENDING, TestDocuments.CREATED_AT);

        transactionTemplate.executeWithoutResult(status -> {
            tweetRepository.insert(tweet);
            outboxMessageRepository.insert(message);
        });

        assertThat(tweetRepository.findById(tweet.getId())).isPresent();
        assertThat(outboxMessageRepository.findById(message.getId())).isPresent();
    }

    @Test
    void should_store_neither_document_when_the_transaction_fails_after_the_tweet_insert() {
        Tweet tweet = TestDocuments.tweet();
        OutboxMessage message = TestDocuments.outboxMessage(OutboxStatus.PENDING, TestDocuments.CREATED_AT);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            tweetRepository.insert(tweet);
            outboxMessageRepository.insert(message);
            throw new IllegalStateException("forced failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(tweetRepository.findById(tweet.getId())).isEmpty();
        assertThat(outboxMessageRepository.findById(message.getId())).isEmpty();
    }
}
