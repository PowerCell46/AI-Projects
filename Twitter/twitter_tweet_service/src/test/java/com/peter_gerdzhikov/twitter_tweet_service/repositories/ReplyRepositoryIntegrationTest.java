package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.bson.BsonBinary;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.UuidRepresentation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.replies.ReplyRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMongoIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

@DataMongoTest
class ReplyRepositoryIntegrationTest extends AbstractMongoIntegrationTest {

    private static final Instant LATER = Instant.parse("2026-02-01T00:00:00Z");

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ReplyRepository replyRepository;

    @Autowired
    private TweetRepository tweetRepository;

    @Nested
    class FindPage {

        @Test
        void should_return_the_oldest_replies_first_when_the_tweet_has_several() {
            UUID tweetId = UUID.randomUUID();
            Reply oldest = replyRepository.save(TestDocuments.replyAt(tweetId, LATER.minusSeconds(30)));
            Reply newest = replyRepository.save(TestDocuments.replyAt(tweetId, LATER));
            Reply middle = replyRepository.save(TestDocuments.replyAt(tweetId, LATER.minusSeconds(10)));

            List<Reply> firstPage = replyRepository.findFirstPage(tweetId, 2);
            List<Reply> secondPage = replyRepository.findPageAfter(
                    tweetId, firstPage.getLast().getCreatedAt(), firstPage.getLast().getId(), 2);

            assertThat(firstPage)
                    .extracting(Reply::getId)
                    .containsExactly(oldest.getId(), middle.getId());
            assertThat(secondPage)
                    .extracting(Reply::getId)
                    .containsExactly(newest.getId());
        }

        @Test
        void should_order_ties_on_created_at_by_id_when_replies_share_a_timestamp() {
            UUID tweetId = UUID.randomUUID();
            List<Reply> sameInstant = List.of(
                    TestDocuments.replyAt(tweetId, LATER),
                    TestDocuments.replyAt(tweetId, LATER),
                    TestDocuments.replyAt(tweetId, LATER),
                    TestDocuments.replyAt(tweetId, LATER));
            replyRepository.saveAll(sameInstant);
            List<UUID> idsByString = sameInstant
                    .stream()
                    .map(Reply::getId)
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();

            List<Reply> firstPage = replyRepository.findFirstPage(tweetId, 2);
            List<Reply> secondPage = replyRepository.findPageAfter(
                    tweetId, firstPage.getLast().getCreatedAt(), firstPage.getLast().getId(), 2);

            assertThat(firstPage)
                    .extracting(Reply::getId)
                    .containsExactlyElementsOf(idsByString.subList(0, 2));
            assertThat(secondPage)
                    .extracting(Reply::getId)
                    .containsExactlyElementsOf(idsByString.subList(2, 4));
        }

        @Test
        void should_return_the_rest_and_no_next_page_when_the_page_is_the_last() {
            UUID tweetId = UUID.randomUUID();
            Reply first = replyRepository.save(TestDocuments.replyAt(tweetId, LATER.minusSeconds(20)));
            Reply second = replyRepository.save(TestDocuments.replyAt(tweetId, LATER.minusSeconds(10)));
            Reply third = replyRepository.save(TestDocuments.replyAt(tweetId, LATER));

            List<Reply> lastPage = replyRepository.findPageAfter(tweetId, first.getCreatedAt(), first.getId(), 10);
            List<Reply> pastTheEnd = replyRepository.findPageAfter(tweetId, third.getCreatedAt(), third.getId(), 10);

            assertThat(lastPage)
                    .extracting(Reply::getId)
                    .containsExactly(second.getId(), third.getId());
            assertThat(pastTheEnd).isEmpty();
        }

        @Test
        void should_leave_out_the_replies_of_other_tweets_when_a_page_is_read() {
            UUID tweetId = UUID.randomUUID();
            Reply own = replyRepository.save(TestDocuments.replyOn(tweetId));
            replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));

            assertThat(replyRepository.findFirstPage(tweetId, 10))
                    .extracting(Reply::getId)
                    .containsExactly(own.getId());
        }
    }

    @Nested
    class DeleteAllByTweetId {

        @Test
        void should_remove_only_that_tweets_replies_when_the_tweet_is_deleted() {
            UUID tweetId = UUID.randomUUID();
            replyRepository.saveAll(List.of(TestDocuments.replyOn(tweetId), TestDocuments.replyOn(tweetId)));
            Reply ofAnotherTweet = replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));

            long deleted = replyRepository.deleteAllByTweetId(tweetId);

            assertThat(deleted).isEqualTo(2);
            assertThat(replyRepository.findFirstPage(tweetId, 10)).isEmpty();
            assertThat(replyRepository.findById(ofAnotherTweet.getId())).isPresent();
        }
    }

    @Nested
    class DeleteByIdAndTweetId {

        @Test
        void should_remove_the_reply_when_it_belongs_to_the_tweet() {
            Reply reply = replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));

            boolean deleted = replyRepository.deleteByIdAndTweetId(reply.getId(), reply.getTweetId());

            assertThat(deleted).isTrue();
            assertThat(replyRepository.findById(reply.getId())).isEmpty();
        }

        @Test
        void should_remove_nothing_when_the_reply_belongs_to_another_tweet() {
            Reply reply = replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));

            boolean deleted = replyRepository.deleteByIdAndTweetId(reply.getId(), UUID.randomUUID());

            assertThat(deleted).isFalse();
            assertThat(replyRepository.findById(reply.getId())).isPresent();
        }
    }

    @Nested
    class UpdateContentIfAuthor {

        @Test
        void should_update_the_reply_when_the_id_the_tweet_and_the_author_match() {
            Reply reply = replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));

            boolean matched = replyRepository.updateContentIfAuthor(
                    reply.getId(), reply.getTweetId(), reply.getAuthorId(), "changed", LATER);

            Reply found = replyRepository.findById(reply.getId()).orElseThrow();
            assertThat(matched).isTrue();
            assertThat(found.getContent()).isEqualTo("changed");
            assertThat(found.isEdited()).isTrue();
            assertThat(found.getUpdatedAt()).isEqualTo(LATER);
            assertThat(found.getCreatedAt()).isEqualTo(reply.getCreatedAt());
        }

        @Test
        void should_match_nothing_when_the_author_differs() {
            Reply reply = replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));

            boolean matched = replyRepository.updateContentIfAuthor(
                    reply.getId(), reply.getTweetId(), UUID.randomUUID(), "changed", LATER);

            assertUnchanged(reply, matched);
        }

        @Test
        void should_match_nothing_when_the_reply_belongs_to_another_tweet() {
            Reply reply = replyRepository.save(TestDocuments.replyOn(UUID.randomUUID()));

            boolean matched = replyRepository.updateContentIfAuthor(
                    reply.getId(), UUID.randomUUID(), reply.getAuthorId(), "changed", LATER);

            assertUnchanged(reply, matched);
        }

        private void assertUnchanged(Reply reply, boolean matched) {
            Reply found = replyRepository.findById(reply.getId()).orElseThrow();

            assertThat(matched).isFalse();
            assertThat(found.getContent()).isEqualTo(reply.getContent());
            assertThat(found.isEdited()).isFalse();
            assertThat(found.getUpdatedAt()).isEqualTo(reply.getUpdatedAt());
        }
    }

    @Nested
    class IncrementReplyCount {

        @Test
        void should_leave_updated_at_alone_when_the_reply_count_is_incremented() {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());

            boolean matched = tweetRepository.incrementReplyCount(tweet.getId(), 1);

            Tweet found = tweetRepository.findById(tweet.getId()).orElseThrow();
            assertThat(matched).isTrue();
            assertThat(found.getReplyCount()).isEqualTo(1);
            assertThat(found.getUpdatedAt()).isEqualTo(tweet.getUpdatedAt());
            assertThat(found.getContent()).isEqualTo(tweet.getContent());
        }

        @Test
        void should_count_down_when_the_delta_is_negative() {
            Tweet tweet = tweetRepository.save(TestDocuments.tweet());
            tweetRepository.incrementReplyCount(tweet.getId(), 2);

            tweetRepository.incrementReplyCount(tweet.getId(), -1);

            assertThat(tweetRepository.findById(tweet.getId()).orElseThrow().getReplyCount()).isEqualTo(1);
        }

        @Test
        void should_match_nothing_when_the_tweet_is_missing() {
            boolean matched = tweetRepository.incrementReplyCount(UUID.randomUUID(), 1);

            assertThat(matched).isFalse();
        }

        @Test
        void should_read_zero_when_the_tweet_has_no_reply_count_field() {
            UUID id = UUID.randomUUID();
            mongoTemplate
                    .getDb()
                    .getCollection("tweets", BsonDocument.class)
                    .insertOne(new BsonDocument()
                            .append("_id", new BsonBinary(id, UuidRepresentation.STANDARD))
                            .append("authorId", new BsonBinary(UUID.randomUUID(), UuidRepresentation.STANDARD))
                            .append("content", new BsonString("stored before replies existed")));

            Tweet found = tweetRepository.findById(id).orElseThrow();

            assertThat(found.getReplyCount()).isZero();
        }
    }
}
