package com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.profiles;

import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.PictureSlot;

public interface ProfilePictureService {

    /**
     * Replaces the picture in the slot. The database never points at a missing object: the new object is
     * stored first, and the old one is deleted only after the database change commits.
     *
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.EmptyUploadException
     *         for an empty file
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.UnsupportedImageTypeException
     *         when the bytes are not a JPEG, PNG or WebP
     * @throws com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException
     *         when storing the new object fails, before any database change
     */
    ProfileResponseDTO upload(UUID userId, PictureSlot slot, MultipartFile file);

    /**
     * Idempotent: a user with no picture in the slot is left as is.
     */
    void delete(UUID userId, PictureSlot slot);
}
