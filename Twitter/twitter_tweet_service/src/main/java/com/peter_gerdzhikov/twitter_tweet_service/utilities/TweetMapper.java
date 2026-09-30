package com.peter_gerdzhikov.twitter_tweet_service.utilities;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetImageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;

public final class TweetMapper {

    private TweetMapper() {
    }

    public static TweetResponseDTO toResponse(Tweet tweet) {
        return TweetResponseDTO
                .builder()
                .id(tweet.getId())
                .views(tweet.getViews())
                .authorId(tweet.getAuthorId())
                .content(tweet.getContent())
                .createdAt(tweet.getCreatedAt())
                .updatedAt(tweet.getUpdatedAt())
                .images(tweet
                        .getImages()
                        .stream()
                        .map(TweetMapper::toResponse)
                        .toList())
                .build();
    }

    private static TweetImageResponseDTO toResponse(TweetImage image) {
        return TweetImageResponseDTO
                .builder()
                .id(image.getId())
                .sizeBytes(image.getSizeBytes())
                .contentType(image.getContentType())
                .build();
    }
}
