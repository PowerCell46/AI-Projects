package com.peter_gerdzhikov.twitter_tweet_service.repositories.replies;

import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;

public interface ReplyRepository extends MongoRepository<Reply, UUID>, ReplyRepositoryCustom {
}
