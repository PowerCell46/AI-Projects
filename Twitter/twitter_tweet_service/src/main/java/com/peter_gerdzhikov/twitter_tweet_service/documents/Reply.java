package com.peter_gerdzhikov.twitter_tweet_service.documents;

import java.util.UUID;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Document(collection = "replies")
@CompoundIndex(name = "ix_replies_tweet_created_id", def = "{'tweetId': 1, 'createdAt': 1, '_id': 1}")
public class Reply extends CommonDocument {

    private UUID tweetId;

    private UUID authorId;

    private String content;

    private boolean edited;
}
