package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.InternalUserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;

public final class ProfileMapper {

    public static final String FILE_URL_PREFIX = "/api/v1/files/";

    private ProfileMapper() {
    }

    public static ProfileResponseDTO toResponse(User user, boolean followedByMe) {
        return ProfileResponseDTO.builder()
                .id(user.getId())
                .bio(user.getBio())
                .username(user.getUsername())
                .location(user.getLocation())
                .createdAt(user.getCreatedAt())
                .birthdate(user.getBirthdate())
                .followedByMe(followedByMe)
                .followersCount(user.getFollowersCount())
                .followingCount(user.getFollowingCount())
                .coverPictureUrl(urlOf(user.getProfileCoverPicture()))
                .profilePictureUrl(urlOf(user.getProfilePicture()))
                .build();
    }

    public static FollowListItemResponseDTO toListItem(User user, boolean followedByMe) {
        return FollowListItemResponseDTO.builder()
                .id(user.getId())
                .bio(user.getBio())
                .username(user.getUsername())
                .followedByMe(followedByMe)
                .profilePictureUrl(urlOf(user.getProfilePicture()))
                .build();
    }

    public static InternalUserResponseDTO toInternalUser(User user) {
        return InternalUserResponseDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .profilePictureUrl(urlOf(user.getProfilePicture()))
                .build();
    }

    private static String urlOf(DbFile file) {
        return file == null ? null : FILE_URL_PREFIX + file.getId();
    }
}
