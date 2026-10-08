package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.PictureSlot;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.profiles.ProfilePictureService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.seed.ProfilePictureSeedService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.files.PathMultipartFile;

import lombok.extern.slf4j.Slf4j;

/**
 * Goes through the profile picture service, so the image is checked and stored exactly like an upload.
 * One bad image is skipped, not fatal: the other users still get theirs.
 */
@Slf4j
@Service
public class ProfilePictureSeedServiceImpl implements ProfilePictureSeedService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path picturesDirectory;

    private final ProfilePictureService profilePictureService;

    public ProfilePictureSeedServiceImpl(
            @Value("${app.seed.pictures-dir}") Path picturesDirectory,
            ProfilePictureService profilePictureService
    ) {
        this.picturesDirectory = picturesDirectory;
        this.profilePictureService = profilePictureService;
    }

    @Override
    public void seedProfilePictures(List<User> users) {
        if (!Files.isDirectory(picturesDirectory)) {
            log.info("No seed pictures directory at '{}'; users keep the default avatar.", picturesDirectory);
            return;
        }

        Map<String, Path> imagesByUsername = findImagesByUsername();
        int uploaded = 0;

        for (User user : users) {
            Path image = imagesByUsername.get(user.getUsername().toLowerCase(Locale.ROOT));
            if (image != null && upload(user, image)) {
                uploaded++;
            }
        }

        log.info("Seeded {} profile pictures.", uploaded);
    }

    private boolean upload(User user, Path image) {
        try {
            profilePictureService.upload(user.getId(), PictureSlot.PROFILE_PICTURE, new PathMultipartFile(image));
            return true;

        } catch (RuntimeException e) {
            log.warn("Could not seed the profile picture '{}'; skipping it.", image.getFileName(), e);
            return false;
        }
    }

    /**
     * File names are matched case-insensitively without the extension: {@code Gosho.PNG} belongs to Gosho.
     */
    private Map<String, Path> findImagesByUsername() {
        Map<String, Path> imagesByUsername = new HashMap<>();

        try (Stream<Path> files = Files.list(picturesDirectory)) {
            files.forEach(file -> {
                String name = file
                        .getFileName()
                        .toString()
                        .toLowerCase(Locale.ROOT);
                int dot = name.lastIndexOf('.');
                if (dot > 0 && IMAGE_EXTENSIONS.contains(name.substring(dot + 1))) {
                    imagesByUsername.put(name.substring(0, dot), file);
                }
            });

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return imagesByUsername;
    }
}
