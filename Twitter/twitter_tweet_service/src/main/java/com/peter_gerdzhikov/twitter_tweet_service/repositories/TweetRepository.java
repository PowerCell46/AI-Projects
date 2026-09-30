package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;

public interface TweetRepository extends MongoRepository<Tweet, UUID>, TweetRepositoryCustom {
}
