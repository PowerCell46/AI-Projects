package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bson.BsonBinary;
import org.bson.BsonBinarySubType;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import com.mongodb.ExplainVerbosity;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMongoIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

@DataMongoTest
class TweetRepositoryIntegrationTest extends AbstractMongoIntegrationTest {

    private static final int READ_LIMIT = 50;

    private static final int TWEETS_IN_THE_WINDOW = 120;

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

    @Nested
    class FindNewestByAuthorSince {

        private static final Instant SINCE = Instant.parse("2026-03-01T00:00:00Z");

        @Test
        void should_return_the_tweets_newest_first_when_the_author_has_several() {
            UUID authorId = UUID.randomUUID();
            Tweet oldest = savedBy(authorId, SINCE.plusSeconds(10));
            Tweet newest = savedBy(authorId, SINCE.plusSeconds(30));
            Tweet middle = savedBy(authorId, SINCE.plusSeconds(20));

            List<Tweet> found = tweetRepository.findNewestByAuthorSince(authorId, SINCE, 10);

            assertThat(found)
                    .extracting(Tweet::getId)
                    .containsExactly(newest.getId(), middle.getId(), oldest.getId());
        }

        @Test
        void should_include_a_tweet_created_exactly_at_since() {
            UUID authorId = UUID.randomUUID();
            Tweet atSince = savedBy(authorId, SINCE);

            List<Tweet> found = tweetRepository.findNewestByAuthorSince(authorId, SINCE, 10);

            assertThat(found)
                    .extracting(Tweet::getId)
                    .containsExactly(atSince.getId());
        }

        @Test
        void should_leave_out_a_tweet_created_before_since() {
            UUID authorId = UUID.randomUUID();
            savedBy(authorId, SINCE.minusMillis(1));

            assertThat(tweetRepository.findNewestByAuthorSince(authorId, SINCE, 10)).isEmpty();
        }

        @Test
        void should_return_only_the_newest_tweets_when_there_are_more_than_the_limit() {
            UUID authorId = UUID.randomUUID();
            savedBy(authorId, SINCE.plusSeconds(10));
            Tweet newest = savedBy(authorId, SINCE.plusSeconds(30));
            Tweet middle = savedBy(authorId, SINCE.plusSeconds(20));

            List<Tweet> found = tweetRepository.findNewestByAuthorSince(authorId, SINCE, 2);

            assertThat(found)
                    .extracting(Tweet::getId)
                    .containsExactly(newest.getId(), middle.getId());
        }

        @Test
        void should_leave_out_the_tweets_of_other_authors() {
            UUID authorId = UUID.randomUUID();
            Tweet own = savedBy(authorId, SINCE.plusSeconds(10));
            savedBy(UUID.randomUUID(), SINCE.plusSeconds(20));

            List<Tweet> found = tweetRepository.findNewestByAuthorSince(authorId, SINCE, 10);

            assertThat(found)
                    .extracting(Tweet::getId)
                    .containsExactly(own.getId());
        }

        @Test
        void should_return_nothing_when_the_author_has_no_tweets() {
            assertThat(tweetRepository.findNewestByAuthorSince(UUID.randomUUID(), SINCE, 10)).isEmpty();
        }

        @Test
        void should_order_tweets_with_the_same_created_at_by_id_descending() {
            UUID authorId = UUID.randomUUID();
            Instant sameInstant = SINCE.plusSeconds(10);
            Tweet first = savedBy(authorId, sameInstant);
            Tweet second = savedBy(authorId, sameInstant);
            Tweet third = savedBy(authorId, sameInstant);
            // Mongo orders binary UUIDs byte by byte, which is the order of their lower-case hex strings.

            List<Tweet> found = tweetRepository.findNewestByAuthorSince(authorId, SINCE, 10);

            assertThat(found)
                    .extracting(Tweet::getId)
                    .isSortedAccordingTo(Comparator.comparing(UUID::toString).reversed())
                    .containsExactlyInAnyOrder(first.getId(), second.getId(), third.getId());
        }

        @Test
        void should_load_only_the_id_and_created_at() {
            UUID authorId = UUID.randomUUID();
            Tweet tweet = TestDocuments.tweetBy(authorId);
            tweet.setCreatedAt(SINCE.plusSeconds(10));
            tweet.setImages(TestDocuments.images(1));
            tweetRepository.save(tweet);

            Tweet found = tweetRepository.findNewestByAuthorSince(authorId, SINCE, 10).getFirst();

            assertThat(found.getId()).isEqualTo(tweet.getId());
            assertThat(found.getCreatedAt()).isEqualTo(tweet.getCreatedAt());
            assertThat(found.getContent()).isNull();
            assertThat(found.getAuthorId()).isNull();
            assertThat(found.getImages()).isEmpty();
        }
    }

    @Nested
    class Indexes {

        @Test
        void should_have_the_author_created_at_and_id_compound_index() {
            List<IndexInfo> indexes = mongoTemplate
                    .indexOps(Tweet.class)
                    .getIndexInfo();

            assertThat(indexes)
                    .filteredOn(index -> index.getName().equals("ix_tweets_author_created_id"))
                    .singleElement()
                    .satisfies(index -> {
                        assertThat(index.getIndexFields())
                                .extracting(field -> field.getKey())
                                .containsExactly("authorId", "createdAt", "_id");
                        assertThat(index.getIndexFields().get(1).getDirection())
                                .isEqualTo(Sort.Direction.DESC);
                        assertThat(index.getIndexFields().get(2).getDirection())
                                .isEqualTo(Sort.Direction.DESC);
                    });
        }

        @Test
        void should_use_the_index_when_the_newest_tweets_of_an_author_are_read() {
            Document plan = mongoTemplate
                    .getCollection("tweets")
                    .find(Filters.and(
                            Filters.eq("authorId", UUID.randomUUID()),
                            Filters.gte("createdAt", Date.from(Instant.parse("2026-03-01T00:00:00Z")))))
                    .sort(Sorts.orderBy(Sorts.descending("createdAt"), Sorts.descending("_id")))
                    .limit(50)
                    .explain();

            assertThat(plan.toJson())
                    .contains("IXSCAN")
                    .contains("ix_tweets_author_created_id")
                    .doesNotContain("COLLSCAN");
        }

        @Test
        void should_read_no_more_documents_than_the_limit_when_an_author_has_many_tweets_in_the_window() {
            UUID authorId = UUID.randomUUID();
            Instant firstCreatedAt = Instant.parse("2026-03-01T00:00:00Z");

            for (int second = 0; second < TWEETS_IN_THE_WINDOW; second++) {
                savedBy(authorId, firstCreatedAt.plusSeconds(second));
            }

            Document plan = mongoTemplate
                    .getCollection("tweets")
                    .find(Filters.and(
                            Filters.eq("authorId", authorId),
                            Filters.gte("createdAt", Date.from(firstCreatedAt))))
                    .sort(Sorts.orderBy(Sorts.descending("createdAt"), Sorts.descending("_id")))
                    .limit(READ_LIMIT)
                    .explain(ExplainVerbosity.EXECUTION_STATS);

            Document executionStats = plan.get("executionStats", Document.class);

            assertThat(executionStats.getInteger("totalKeysExamined")).isLessThanOrEqualTo(READ_LIMIT);
            assertThat(executionStats.getInteger("totalDocsExamined")).isLessThanOrEqualTo(READ_LIMIT);
        }
    }

    private Tweet savedBy(UUID authorId, Instant createdAt) {
        Tweet tweet = TestDocuments.tweetBy(authorId);
        tweet.setCreatedAt(createdAt);

        return tweetRepository.save(tweet);
    }

    private BsonDocument rawDocument(UUID id) {
        return mongoTemplate
                .getDb()
                .getCollection("tweets", BsonDocument.class)
                .find(new BsonDocument("_id", new BsonBinary(id, UuidRepresentation.STANDARD)))
                .first();
    }
}
