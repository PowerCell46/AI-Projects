package com.peter_gerdzhikov.twitter_timeline_service.utilities.mappers;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetImageClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.AuthorResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetImageResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;

public final class TweetItemMapper {

    private TweetItemMapper() {
    }

    public static TweetItemResponseDTO toItem(
            TweetClientDTO tweet,
            UserClientDTO author,
            long views,
            boolean savedByMe,
            long likes,
            boolean likedByMe
    ) {
        return TweetItemResponseDTO
                .builder()
                .id(tweet.getId())
                .views(views)
                .savedByMe(savedByMe)
                .likes(likes)
                .likedByMe(likedByMe)
                .content(tweet.getContent())
                .createdAt(tweet.getCreatedAt())
                .updatedAt(tweet.getUpdatedAt())
                .author(toAuthor(author))
                .images(tweet
                        .getImages()
                        .stream()
                        .map(TweetItemMapper::toImage)
                        .toList())
                .build();
    }

    private static AuthorResponseDTO toAuthor(UserClientDTO author) {
        return AuthorResponseDTO
                .builder()
                .id(author.getId())
                .username(author.getUsername())
                .profilePictureUrl(author.getProfilePictureUrl())
                .build();
    }

    private static TweetImageResponseDTO toImage(TweetImageClientDTO image) {
        return TweetImageResponseDTO
                .builder()
                .id(image.getId())
                .sizeBytes(image.getSizeBytes())
                .contentType(image.getContentType())
                .build();
    }
}
