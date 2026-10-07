package com.peter_gerdzhikov.twitter_tweet_service.utilities.mappers;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetImageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetSummaryResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;

public final class TweetMapper {

    private TweetMapper() {
    }

    public static TweetResponseDTO toResponse(Tweet tweet) {
        return TweetResponseDTO
                .builder()
                .id(tweet.getId())
                .authorId(tweet.getAuthorId())
                .content(tweet.getContent())
                .replyCount(tweet.getReplyCount())
                .createdAt(tweet.getCreatedAt())
                .updatedAt(tweet.getUpdatedAt())
                .images(tweet
                        .getImages()
                        .stream()
                        .map(TweetMapper::toResponse)
                        .toList())
                .build();
    }

    public static TweetSummaryResponseDTO toSummary(Tweet tweet) {
        return TweetSummaryResponseDTO
                .builder()
                .id(tweet.getId())
                .createdAt(tweet.getCreatedAt())
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
