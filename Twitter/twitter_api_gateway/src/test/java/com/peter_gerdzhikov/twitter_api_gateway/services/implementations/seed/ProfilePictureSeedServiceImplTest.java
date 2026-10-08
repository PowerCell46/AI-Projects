package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.seed;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.PictureSlot;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.UnsupportedImageTypeException;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.profiles.ProfilePictureService;

@ExtendWith(MockitoExtension.class)
class ProfilePictureSeedServiceImplTest {

    @Mock
    private ProfilePictureService profilePictureService;

    @TempDir
    private Path directory;

    @Nested
    @DisplayName("seedProfilePictures")
    class SeedProfilePictures {

        @Test
        void should_upload_the_image_named_after_the_username_ignoring_case() throws IOException {
            Files.writeString(directory.resolve("GOSHO.PNG"), "image");
            User gosho = newUser("Gosho");

            newSeedService(directory).seedProfilePictures(List.of(gosho));

            verify(profilePictureService).upload(eq(gosho.getId()), eq(PictureSlot.PROFILE_PICTURE), any());
        }

        @Test
        void should_skip_a_user_without_an_image_and_a_file_that_is_not_an_image() throws IOException {
            Files.writeString(directory.resolve("gabi.txt"), "not an image");

            newSeedService(directory).seedProfilePictures(List.of(newUser("Gabi")));

            verifyNoInteractions(profilePictureService);
        }

        @Test
        void should_do_nothing_when_the_directory_does_not_exist() {
            newSeedService(directory.resolve("missing")).seedProfilePictures(List.of(newUser("Gosho")));

            verifyNoInteractions(profilePictureService);
        }

        @Test
        void should_keep_going_when_one_image_is_rejected() throws IOException {
            Files.writeString(directory.resolve("gosho.png"), "bad");
            Files.writeString(directory.resolve("gabi.png"), "image");
            User gosho = newUser("Gosho");
            User gabi = newUser("Gabi");
            when(profilePictureService.upload(eq(gosho.getId()), any(), any()))
                    .thenThrow(new UnsupportedImageTypeException());

            newSeedService(directory).seedProfilePictures(List.of(gosho, gabi));

            verify(profilePictureService).upload(eq(gabi.getId()), eq(PictureSlot.PROFILE_PICTURE), any());
            verify(profilePictureService, never()).delete(any(), any());
        }
    }

    private ProfilePictureSeedServiceImpl newSeedService(Path picturesDirectory) {
        return new ProfilePictureSeedServiceImpl(picturesDirectory, profilePictureService);
    }

    private User newUser(String username) {
        User user = User.builder()
                .username(username)
                .build();
        user.setId(UUID.randomUUID());

        return user;
    }
}
