package com.peter_gerdzhikov.twitter_tweet_service.utilities.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

class ReplyMapperTest {

    private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00.456Z");

    @Test
    void toResponse_copiesTheReplyAndTheAuthor() {
        Reply reply = TestDocuments.replyOn(UUID.randomUUID());
        reply.setEdited(true);
        reply.setUpdatedAt(UPDATED_AT);
        UserClientDTO author = UserClientDTO
                .builder()
                .id(UUID.randomUUID())
                .username("ana")
                .profilePictureUrl("/api/v1/files/pic")
                .build();

        ReplyResponseDTO response = ReplyMapper.toResponse(reply, author);

        assertThat(response.getId()).isEqualTo(reply.getId());
        assertThat(response.getTweetId()).isEqualTo(reply.getTweetId());
        assertThat(response.getContent()).isEqualTo("a reply");
        assertThat(response.isEdited()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(TestDocuments.CREATED_AT);
        assertThat(response.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(response.getAuthor().getId()).isEqualTo(author.getId());
        assertThat(response.getAuthor().getUsername()).isEqualTo("ana");
        assertThat(response.getAuthor().getProfilePictureUrl()).isEqualTo("/api/v1/files/pic");
    }

    @Test
    void toResponse_leavesThePictureUrlNullWhenTheAuthorHasNone() {
        UserClientDTO author = UserClientDTO
                .builder()
                .id(UUID.randomUUID())
                .username("bob")
                .build();

        ReplyResponseDTO response = ReplyMapper.toResponse(TestDocuments.replyOn(UUID.randomUUID()), author);

        assertThat(response.getAuthor().getProfilePictureUrl()).isNull();
    }

    @Test
    void toResponse_marksTheReplyNotEditedByDefault() {
        UserClientDTO author = UserClientDTO
                .builder()
                .id(UUID.randomUUID())
                .username("bob")
                .build();

        assertThat(ReplyMapper.toResponse(TestDocuments.replyOn(UUID.randomUUID()), author).isEdited()).isFalse();
    }
}
