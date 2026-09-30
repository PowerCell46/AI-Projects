package com.peter_gerdzhikov.twitter_tweet_service.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
public class Tweet extends CommonDocument {

    private long views;

    private UUID authorId;

    private String content;

    @Builder.Default
    private List<TweetImage> images = new ArrayList<>();
}
