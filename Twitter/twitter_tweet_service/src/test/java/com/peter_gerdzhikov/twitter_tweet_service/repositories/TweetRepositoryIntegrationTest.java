package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMongoIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

@DataMongoTest
class TweetRepositoryIntegrationTest extends AbstractMongoIntegrationTest {

    private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private TweetRepository tweetRepository;

    @Nested
    class SaveAndFind {

        @Test
        void should_round_trip_every_field_when_the_tweet_has_images() {
            Tweet tweet = TestDocuments.tweet();
            tweet.setImages(TestDocuments.images(2));
            tweetRepository.save(tweet);

            Tweet found = tweetRepository.findById(tweet.getId()).orElseThrow();

            assertThat(found)
                    .usingRecursiveComparison()
                    .isEqualTo(tweet);
        }

        @Test
        void should_store_the_ids_as_standard_binary_uuids() {
            Tweet tweet = TestDocuments.tweet();
            tweet.setImages(TestDocuments.images(1));
            tweetRepository.save(tweet);

            BsonDocument stored = rawDocument(tweet.getId());

            assertThat(stored.getBinary("_id").getType()).isEqualTo(BsonBinarySubType.UUID_STANDARD.getValue());
            assertThat(stored.getBinary("authorId").getType()).isEqualTo(BsonBinarySubType.UUID_STANDARD.getValue());
            assertThat(stored.getArray("images").get(0).asDocument().getBinary("_id").getType())
                    .isEqualTo(BsonBinarySubType.UUID_STANDARD.getValue());
        }

        @Test
        void should_default_to_no_images_when_the_tweet_is_built_without_them() {
            Tweet tweet = TestDocuments.tweet();
            tweetRepository.save(tweet);

            Tweet found = tweetRepository.findById(tweet.getId()).orElseThrow();

            assertThat(found.getImages()).isEmpty();
        }
    }

    @Nested
    class UpdateContentIfAuthor {

        @Test
        void should_update_only_the_content_and_updated_at_when_the_author_matches() {
            Tweet tweet = TestDocuments.tweet();
            tweet.setImages(TestDocuments.images(1));
            tweetRepository.save(tweet);

            boolean matched = tweetRepository.updateContentIfAuthor(tweet.getId(), tweet.getAuthorId(), "edited", LATER);

            Tweet found = tweetRepository.findById(tweet.getId()).orElseThrow();
            assertThat(matched).isTrue();
            assertThat(found.getContent()).isEqualTo("edited");
            assertThat(found.getUpdatedAt()).isEqualTo(LATER);
            assertThat(found)
                    .usingRecursiveComparison()
                    .ignoringFields("content", "updatedAt")
                    .isEqualTo(tweet);
        }

        @Test
        void should_match_nothing_and_change_nothing_when_the_author_differs() {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            boolean matched = tweetRepository.updateContentIfAuthor(tweet.getId(), UUID.randomUUID(), "edited", LATER);

            assertThat(matched).isFalse();
            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow())
                    .usingRecursiveComparison()
                    .isEqualTo(tweet);
        }

        @Test
        void should_match_nothing_and_create_nothing_when_the_tweet_is_unknown() {
            UUID unknownId = UUID.randomUUID();

            boolean matched = tweetRepository.updateContentIfAuthor(unknownId, UUID.randomUUID(), "edited", LATER);

            assertThat(matched).isFalse();
            assertThat(tweetRepository.findById(unknownId)).isEmpty();
        }
    }

    @Nested
    class DeleteIfAuthor {

        @Test
        void should_delete_the_tweet_when_the_author_matches() {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            boolean deleted = tweetRepository.deleteIfAuthor(tweet.getId(), tweet.getAuthorId());

            assertThat(deleted).isTrue();
            assertThat(tweetRepository.findById(tweet.getId())).isEmpty();
        }

        @Test
        void should_delete_nothing_when_the_author_differs() {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            boolean deleted = tweetRepository.deleteIfAuthor(tweet.getId(), UUID.randomUUID());

            assertThat(deleted).isFalse();
            assertThat(tweetRepository.findById(tweet.getId())).isPresent();
        }

        @Test
        void should_report_nothing_deleted_when_the_tweet_is_already_gone() {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            tweetRepository.deleteIfAuthor(tweet.getId(), tweet.getAuthorId());

            assertThat(tweetRepository.deleteIfAuthor(tweet.getId(), tweet.getAuthorId())).isFalse();
        }
    }

    private BsonDocument rawDocument(UUID id) {
        return mongoTemplate
                .getDb()
                .getCollection("tweets", BsonDocument.class)
                .find(new BsonDocument("_id", new BsonBinary(id, UuidRepresentation.STANDARD)))
                .first();
    }
}
