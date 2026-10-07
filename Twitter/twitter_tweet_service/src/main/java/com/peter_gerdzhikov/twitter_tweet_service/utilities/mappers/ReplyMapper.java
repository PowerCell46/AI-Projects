package com.peter_gerdzhikov.twitter_tweet_service.utilities.mappers;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.AuthorResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;

public final class ReplyMapper {

    private ReplyMapper() {
    }

    public static ReplyResponseDTO toResponse(Reply reply, UserClientDTO author) {
        return ReplyResponseDTO
                .builder()
                .id(reply.getId())
                .tweetId(reply.getTweetId())
                .content(reply.getContent())
                .edited(reply.isEdited())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .author(AuthorResponseDTO
                        .builder()
                        .id(author.getId())
                        .username(author.getUsername())
                        .profilePictureUrl(author.getProfilePictureUrl())
                        .build())
                .build();
    }
}
