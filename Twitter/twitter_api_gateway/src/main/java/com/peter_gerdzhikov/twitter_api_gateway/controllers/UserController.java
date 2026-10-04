package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.UpdateProfileRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.PictureSlot;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ProfilePictureService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ProfileService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.UserListService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final String DEFAULT_PAGE_SIZE = "20";

    private final ProfileService profileService;

    private final ProfilePictureService profilePictureService;

    private final UserListService userListService;

    @GetMapping
    public UserListResponseDTO getUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) int size
    ) {
        return userListService.listUsers(UUID.fromString(jwt.getSubject()), cursor, size);
    }

    @GetMapping("/{username}")
    public ProfileResponseDTO getProfile(@AuthenticationPrincipal Jwt jwt, @PathVariable String username) {
        return profileService.getProfile(UUID.fromString(jwt.getSubject()), username);
    }

    @PutMapping("/me")
    public ProfileResponseDTO updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequestDTO request
    ) {
        return profileService.updateProfile(UUID.fromString(jwt.getSubject()), request);
    }

    @PutMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponseDTO uploadProfilePicture(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file
    ) {
        return profilePictureService.upload(UUID.fromString(jwt.getSubject()), PictureSlot.PROFILE_PICTURE, file);
    }

    @PutMapping(value = "/me/cover-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponseDTO uploadCoverPicture(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file
    ) {
        return profilePictureService.upload(UUID.fromString(jwt.getSubject()), PictureSlot.COVER_PICTURE, file);
    }

    @DeleteMapping("/me/profile-picture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProfilePicture(@AuthenticationPrincipal Jwt jwt) {
        profilePictureService.delete(UUID.fromString(jwt.getSubject()), PictureSlot.PROFILE_PICTURE);
    }

    @DeleteMapping("/me/cover-picture")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCoverPicture(@AuthenticationPrincipal Jwt jwt) {
        profilePictureService.delete(UUID.fromString(jwt.getSubject()), PictureSlot.COVER_PICTURE);
    }
}
