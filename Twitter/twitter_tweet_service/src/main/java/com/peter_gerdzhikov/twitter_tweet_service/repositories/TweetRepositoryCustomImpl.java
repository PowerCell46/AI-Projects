package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TweetRepositoryCustomImpl implements TweetRepositoryCustom {

    private final MongoTemplate mongoTemplate;

    @Override
    public boolean updateContentIfAuthor(UUID id, UUID authorId, String content, Instant updatedAt) {
        Update update = new Update()
                .set("content", content)
                .set("updatedAt", updatedAt);

        return mongoTemplate
                .updateFirst(byIdAndAuthor(id, authorId), update, Tweet.class)
                .getMatchedCount() > 0;
    }

    @Override
    public boolean deleteIfAuthor(UUID id, UUID authorId) {
        return mongoTemplate
                .remove(byIdAndAuthor(id, authorId), Tweet.class)
                .getDeletedCount() > 0;
    }

    private Query byIdAndAuthor(UUID id, UUID authorId) {
        return Query.query(Criteria
                .where("id")
                .is(id)
                .and("authorId")
                .is(authorId));
    }
}
