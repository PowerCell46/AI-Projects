package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.UpdateProfileRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private ProfileServiceImpl profileService;

    @Mock
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(userRepository);
    }

    @Nested
    class GetProfile {

        @Test
        void should_look_the_user_up_by_the_lowercased_username() {
            User user = confirmedUser();
            when(userRepository.findByUsernameNormalized("peterg")).thenReturn(Optional.of(user));

            ProfileResponseDTO profile = profileService.getProfile("PeterG");

            assertThat(profile.getUsername()).isEqualTo(user.getUsername());
            verify(userRepository).findByUsernameNormalized("peterg");
        }

        @Test
        void should_throw_when_the_user_is_unknown() {
            when(userRepository.findByUsernameNormalized("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profileService.getProfile("nobody"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void should_throw_when_the_user_is_unconfirmed() {
            User user = TestEntities.newUser();
            when(userRepository.findByUsernameNormalized("pending")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> profileService.getProfile("pending"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void should_overwrite_every_field_when_the_request_is_full() {
            User user = confirmedUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("Hello")
                    .location("Sofia")
                    .birthdate(LocalDate.of(1990, 5, 17))
                    .build();

            ProfileResponseDTO profile = profileService.updateProfile(USER_ID, request);

            assertThat(user.getBio()).isEqualTo("Hello");
            assertThat(user.getLocation()).isEqualTo("Sofia");
            assertThat(user.getBirthdate()).isEqualTo(LocalDate.of(1990, 5, 17));
            assertThat(profile.getBio()).isEqualTo("Hello");
        }

        @Test
        void should_trim_the_strings() {
            User user = confirmedUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("  Hello  ")
                    .location("\tSofia\n")
                    .build();

            profileService.updateProfile(USER_ID, request);

            assertThat(user.getBio()).isEqualTo("Hello");
            assertThat(user.getLocation()).isEqualTo("Sofia");
        }

        @Test
        void should_clear_the_fields_when_they_are_null_or_blank() {
            User user = confirmedUser();
            user.setBio("old bio");
            user.setLocation("old location");
            user.setBirthdate(LocalDate.of(1990, 5, 17));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("   ")
                    .build();

            profileService.updateProfile(USER_ID, request);

            assertThat(user.getBio()).isNull();
            assertThat(user.getLocation()).isNull();
            assertThat(user.getBirthdate()).isNull();
        }

        @Test
        void should_leave_the_pictures_alone() {
            User user = confirmedUser();
            user.setProfilePicture(TestEntities.newDbFile());
            user.setProfileCoverPicture(TestEntities.newDbFile());
            var profilePicture = user.getProfilePicture();
            var coverPicture = user.getProfileCoverPicture();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            profileService.updateProfile(USER_ID, new UpdateProfileRequestDTO());

            assertThat(user.getProfilePicture()).isSameAs(profilePicture);
            assertThat(user.getProfileCoverPicture()).isSameAs(coverPicture);
        }

        @Test
        void should_throw_when_the_user_no_longer_exists() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profileService.updateProfile(USER_ID, new UpdateProfileRequestDTO()))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setUsername("PeterG");
        user.setEnabled(true);

        return user;
    }
}
