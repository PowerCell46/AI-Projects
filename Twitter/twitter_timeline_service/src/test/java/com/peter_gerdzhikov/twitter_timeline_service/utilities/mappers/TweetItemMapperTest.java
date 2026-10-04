package com.peter_gerdzhikov.twitter_timeline_service.utilities.mappers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.TweetImageClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;

class TweetItemMapperTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123Z");

    private static final Instant UPDATED_AT = Instant.parse("2026-01-02T00:00:00.456Z");

    @Test
    void toItem_copiesTheTweetTheImagesAndTheAuthor() {
        UUID authorId = UUID.randomUUID();
        UUID imageId = UUID.randomUUID();
        TweetClientDTO tweet = tweet(authorId, List.of(new TweetImageClientDTO(imageId, 1234, "image/png")));
        UserClientDTO author = new UserClientDTO(authorId, "ana", "/api/v1/files/pic");

        TweetItemResponseDTO item = TweetItemMapper.toItem(tweet, author, 42, true);

        assertThat(item.getId()).isEqualTo(tweet.getId());
        assertThat(item.getContent()).isEqualTo("hello");
        assertThat(item.getViews()).isEqualTo(42);
        assertThat(item.isSavedByMe()).isTrue();
        assertThat(item.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(item.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(item.getImages()).hasSize(1);
        assertThat(item.getImages().getFirst().getId()).isEqualTo(imageId);
        assertThat(item.getImages().getFirst().getSizeBytes()).isEqualTo(1234);
        assertThat(item.getImages().getFirst().getContentType()).isEqualTo("image/png");
        assertThat(item.getAuthor().getId()).isEqualTo(authorId);
        assertThat(item.getAuthor().getUsername()).isEqualTo("ana");
        assertThat(item.getAuthor().getProfilePictureUrl()).isEqualTo("/api/v1/files/pic");
    }

    @Test
    void toItem_returnsNoImagesAndANullPictureUrlWhenThereAreNone() {
        UUID authorId = UUID.randomUUID();

        TweetItemResponseDTO item = TweetItemMapper.toItem(tweet(authorId, List.of()), new UserClientDTO(authorId, "bob", null), 0, false);

        assertThat(item.getImages()).isEmpty();
        assertThat(item.getAuthor().getProfilePictureUrl()).isNull();
    }

    @Test
    void toItem_marksTheItemNotSavedWhenTheViewerDidNotSaveIt() {
        UUID authorId = UUID.randomUUID();

        TweetItemResponseDTO item = TweetItemMapper.toItem(tweet(authorId, List.of()), new UserClientDTO(authorId, "bob", null), 0, false);

        assertThat(item.isSavedByMe()).isFalse();
    }

    private TweetClientDTO tweet(UUID authorId, List<TweetImageClientDTO> images) {
        return TweetClientDTO
                .builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .content("hello")
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .images(images)
                .build();
    }
}
