package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.InternalUserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

class ProfileMapperTest {

    @Test
    void toResponse_copiesTheVisibleFieldsAndBuildsPictureUrls() {
        User user = TestEntities.newUser();
        user.setId(UUID.randomUUID());
        user.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        user.setBio("Hello");
        user.setLocation("Sofia");
        user.setBirthdate(LocalDate.of(1990, 5, 17));
        user.setFollowersCount(7);
        user.setFollowingCount(3);
        DbFile profilePicture = fileWithId();
        DbFile coverPicture = fileWithId();
        user.setProfilePicture(profilePicture);
        user.setProfileCoverPicture(coverPicture);

        ProfileResponseDTO response = ProfileMapper.toResponse(user, true);

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getUsername()).isEqualTo(user.getUsername());
        assertThat(response.getBio()).isEqualTo("Hello");
        assertThat(response.getLocation()).isEqualTo("Sofia");
        assertThat(response.getBirthdate()).isEqualTo(LocalDate.of(1990, 5, 17));
        assertThat(response.getFollowersCount()).isEqualTo(7);
        assertThat(response.getFollowingCount()).isEqualTo(3);
        assertThat(response.isFollowedByMe()).isTrue();
        assertThat(response.getCreatedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(response.getProfilePictureUrl()).isEqualTo("/api/v1/files/" + profilePicture.getId());
        assertThat(response.getCoverPictureUrl()).isEqualTo("/api/v1/files/" + coverPicture.getId());
    }

    @Test
    void toResponse_returnsNullUrlsWhenThereAreNoPictures() {
        ProfileResponseDTO response = ProfileMapper.toResponse(TestEntities.newUser(), false);

        assertThat(response.getProfilePictureUrl()).isNull();
        assertThat(response.getCoverPictureUrl()).isNull();
    }

    @Test
    void toListItem_copiesTheListFieldsAndBuildsThePictureUrl() {
        User user = TestEntities.newUser();
        user.setId(UUID.randomUUID());
        user.setBio("Hello");
        DbFile profilePicture = fileWithId();
        user.setProfilePicture(profilePicture);

        FollowListItemResponseDTO item = ProfileMapper.toListItem(user, true);

        assertThat(item.getId()).isEqualTo(user.getId());
        assertThat(item.getUsername()).isEqualTo(user.getUsername());
        assertThat(item.getBio()).isEqualTo("Hello");
        assertThat(item.isFollowedByMe()).isTrue();
        assertThat(item.getProfilePictureUrl()).isEqualTo("/api/v1/files/" + profilePicture.getId());
    }

    @Test
    void toListItem_returnsNullUrlWhenThereIsNoPicture() {
        assertThat(ProfileMapper.toListItem(TestEntities.newUser(), false).getProfilePictureUrl()).isNull();
    }

    @Test
    void toInternalUser_copiesTheIdUsernameAndBuildsThePictureUrl() {
        User user = TestEntities.newUser();
        user.setId(UUID.randomUUID());
        DbFile profilePicture = fileWithId();
        user.setProfilePicture(profilePicture);

        InternalUserResponseDTO response = ProfileMapper.toInternalUser(user);

        assertThat(response.getId()).isEqualTo(user.getId());
        assertThat(response.getUsername()).isEqualTo(user.getUsername());
        assertThat(response.getProfilePictureUrl()).isEqualTo("/api/v1/files/" + profilePicture.getId());
    }

    @Test
    void toInternalUser_returnsANullUrlWhenThereIsNoPicture() {
        User user = TestEntities.newUser();
        user.setId(UUID.randomUUID());

        assertThat(ProfileMapper.toInternalUser(user).getProfilePictureUrl()).isNull();
    }

    @Test
    void toUserListItem_copiesTheSixFieldsAndBuildsThePictureUrl() {
        User user = TestEntities.newUser();
        user.setId(UUID.randomUUID());
        user.setBio("Hello");
        user.setFollowersCount(7);
        DbFile profilePicture = fileWithId();
        user.setProfilePicture(profilePicture);

        UserListItemResponseDTO item = ProfileMapper.toUserListItem(user, true);

        assertThat(item.getId()).isEqualTo(user.getId());
        assertThat(item.getUsername()).isEqualTo(user.getUsername());
        assertThat(item.getBio()).isEqualTo("Hello");
        assertThat(item.getFollowersCount()).isEqualTo(7);
        assertThat(item.isFollowedByMe()).isTrue();
        assertThat(item.getProfilePictureUrl()).isEqualTo("/api/v1/files/" + profilePicture.getId());
    }

    @Test
    void toUserListItem_returnsANullPictureUrlAndANullBioWhenTheUserHasNeither() {
        UserListItemResponseDTO item = ProfileMapper.toUserListItem(TestEntities.newUser(), false);

        assertThat(item.getProfilePictureUrl()).isNull();
        assertThat(item.getBio()).isNull();
        assertThat(item.isFollowedByMe()).isFalse();
    }

    private DbFile fileWithId() {
        DbFile file = TestEntities.newDbFile();
        file.setId(UUID.randomUUID());

        return file;
    }
}
