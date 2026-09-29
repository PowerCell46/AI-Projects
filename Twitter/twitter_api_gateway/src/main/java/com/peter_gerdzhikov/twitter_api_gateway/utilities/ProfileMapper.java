package com.peter_gerdzhikov.twitter_api_gateway.utilities;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;

public final class ProfileMapper {

    public static final String FILE_URL_PREFIX = "/api/v1/files/";

    private ProfileMapper() {
    }

    public static ProfileResponseDTO toResponse(User user) {
        return ProfileResponseDTO.builder()
                .id(user.getId())
                .bio(user.getBio())
                .username(user.getUsername())
                .location(user.getLocation())
                .createdAt(user.getCreatedAt())
                .birthdate(user.getBirthdate())
                .coverPictureUrl(urlOf(user.getProfileCoverPicture()))
                .profilePictureUrl(urlOf(user.getProfilePicture()))
                .build();
    }

    private static String urlOf(DbFile file) {
        return file == null ? null : FILE_URL_PREFIX + file.getId();
    }
}
