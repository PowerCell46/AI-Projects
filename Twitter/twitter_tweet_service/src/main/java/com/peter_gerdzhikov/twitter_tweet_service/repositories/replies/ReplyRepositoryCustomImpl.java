package com.peter_gerdzhikov.twitter_tweet_service.repositories.replies;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReplyRepositoryCustomImpl implements ReplyRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<Reply> findFirstPage(UUID tweetId, int limit) {
        Query query = Query
                .query(Criteria
                        .where("tweetId")
                        .is(tweetId));

        return findOldestFirst(query, limit);
    }

    @Override
    public List<Reply> findPageAfter(UUID tweetId, Instant afterCreatedAt, UUID afterId, int limit) {
        Criteria afterThePosition = new Criteria().orOperator(
                Criteria
                        .where("createdAt")
                        .gt(afterCreatedAt),
                Criteria
                        .where("createdAt")
                        .is(afterCreatedAt)
                        .and("id")
                        .gt(afterId));
        Query query = Query.query(new Criteria().andOperator(
                Criteria
                        .where("tweetId")
                        .is(tweetId),
                afterThePosition));

        return findOldestFirst(query, limit);
    }

    @Override
    public boolean updateContentIfAuthor(UUID id, UUID tweetId, UUID authorId, String content, Instant updatedAt) {
        Query byIdTweetAndAuthor = Query.query(Criteria
                .where("id")
                .is(id)
                .and("tweetId")
                .is(tweetId)
                .and("authorId")
                .is(authorId));
        Update update = new Update()
                .set("content", content)
                .set("edited", true)
                .set("updatedAt", updatedAt);

        return mongoTemplate
                .updateFirst(byIdTweetAndAuthor, update, Reply.class)
                .getMatchedCount() > 0;
    }

    @Override
    public boolean deleteByIdAndTweetId(UUID id, UUID tweetId) {
        Query byIdAndTweet = Query.query(Criteria
                .where("id")
                .is(id)
                .and("tweetId")
                .is(tweetId));

        return mongoTemplate
                .remove(byIdAndTweet, Reply.class)
                .getDeletedCount() > 0;
    }

    @Override
    public long deleteAllByTweetId(UUID tweetId) {
        Query byTweet = Query.query(Criteria
                .where("tweetId")
                .is(tweetId));

        return mongoTemplate
                .remove(byTweet, Reply.class)
                .getDeletedCount();
    }

    private List<Reply> findOldestFirst(Query query, int limit) {
        query
                .with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")))
                .limit(limit);

        return mongoTemplate.find(query, Reply.class);
    }
}
