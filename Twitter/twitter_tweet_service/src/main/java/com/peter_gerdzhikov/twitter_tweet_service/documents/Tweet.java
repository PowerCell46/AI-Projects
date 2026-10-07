package com.peter_gerdzhikov.twitter_tweet_service.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Document(collection = "tweets")
@CompoundIndex(name = "ix_tweets_author_created_id", def = "{'authorId': 1, 'createdAt': -1, '_id': -1}")
public class Tweet extends CommonDocument {

    private UUID authorId;

    private String content;

    /**
     * Changed only by an increment inside the transaction that adds or removes a reply, never by a full save,
     * so a tweet stored before replies existed has no such field and reads as zero.
     */
    private long replyCount;

    @Builder.Default
    private List<TweetImage> images = new ArrayList<>();
}
