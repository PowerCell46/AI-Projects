package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.PictureSlot;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.EmptyUploadException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ObjectStorageService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ProfilePictureService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.ImageSignatureValidator;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.ProfileMapper;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Uses a {@link TransactionTemplate} rather than {@code @Transactional}: the storage calls before and after
 * the database change must stay outside the transaction, so the old object is only deleted once the commit
 * has happened.
 */
@Slf4j
@Service
public class ProfilePictureServiceImpl implements ProfilePictureService {

    private final long maxFileBytes;

    private final UserRepository userRepository;

    private final DbFileRepository dbFileRepository;

    private final TransactionTemplate transactionTemplate;

    private final ObjectStorageService objectStorageService;

    public ProfilePictureServiceImpl(
            @Value("${app.upload.max-file-bytes}") long maxFileBytes,
            UserRepository userRepository,
            DbFileRepository dbFileRepository,
            TransactionTemplate transactionTemplate,
            ObjectStorageService objectStorageService
    ) {
        this.maxFileBytes = maxFileBytes;
        this.userRepository = userRepository;
        this.dbFileRepository = dbFileRepository;
        this.transactionTemplate = transactionTemplate;
        this.objectStorageService = objectStorageService;
    }

    @Override
    public ProfileResponseDTO upload(UUID userId, PictureSlot slot, MultipartFile file) {
        String contentType = detectContentType(file);
        String newObjectKey = UUID.randomUUID().toString();
        store(newObjectKey, file, contentType);

        Replacement replacement;
        try {
            replacement = transactionTemplate.execute(status ->
                    repoint(userId, slot, newObjectKey, contentType, file.getSize()));

        } catch (RuntimeException e) {
            deleteObjectQuietly(newObjectKey);
            throw e;
        }

        deleteObjectQuietly(replacement.getReplacedObjectKey());

        return replacement.getResponse();
    }

    @Override
    public void delete(UUID userId, PictureSlot slot) {
        String removedObjectKey = transactionTemplate.execute(status -> clear(userId, slot));

        deleteObjectQuietly(removedObjectKey);
    }

    private String detectContentType(MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyUploadException();
        }

        if (file.getSize() > maxFileBytes) {
            throw new MaxUploadSizeExceededException(maxFileBytes);
        }

        try (InputStream content = file.getInputStream()) {
            return ImageSignatureValidator.detectContentType(content.readNBytes(ImageSignatureValidator.HEADER_BYTES));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void store(String objectKey, MultipartFile file, String contentType) {
        try (InputStream content = file.getInputStream()) {
            objectStorageService.put(objectKey, content, file.getSize(), contentType);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Replacement repoint(UUID userId, PictureSlot slot, String objectKey, String contentType, long sizeBytes) {
        User user = loadLockedUser(userId);
        DbFile replaced = slot.read(user);
        DbFile stored = dbFileRepository.save(DbFile.builder()
                .sizeBytes(sizeBytes)
                .objectKey(objectKey)
                .contentType(contentType)
                .build());
        slot.write(user, stored);
        if (replaced != null) {
            dbFileRepository.delete(replaced);
        }

        return new Replacement(ProfileMapper.toResponse(user, false), replaced == null ? null : replaced.getObjectKey());
    }

    private String clear(UUID userId, PictureSlot slot) {
        User user = loadLockedUser(userId);
        DbFile removed = slot.read(user);
        if (removed == null) {
            return null;
        }

        slot.write(user, null);
        dbFileRepository.delete(removed);

        return removed.getObjectKey();
    }

    /**
     * Same-user changes must serialise: without the lock, concurrent uploads all read the same old picture,
     * and every one but the last pointer write leaves an unreferenced row and object behind.
     */
    private User loadLockedUser(UUID userId) {
        userRepository
                .lockById(userId)
                .orElseThrow(UserNotFoundException::new);

        return userRepository
                .findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }

    private void deleteObjectQuietly(String objectKey) {
        if (objectKey == null) {
            return;
        }

        try {
            objectStorageService.delete(objectKey);

        } catch (StorageUnavailableException e) {
            log.warn("Could not delete object {}; it is now an orphan.", objectKey);
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static class Replacement {

        private final ProfileResponseDTO response;

        private final String replacedObjectKey;
    }
}
