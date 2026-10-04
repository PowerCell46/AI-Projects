package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.profiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private static final UUID VIEWER_ID = UUID.randomUUID();

    private ProfileServiceImpl profileService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowRepository followRepository;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(userRepository, followRepository);
    }

    @Nested
    class GetProfile {

        @Test
        void should_look_the_user_up_by_the_lowercased_username() {
            User user = confirmedUser();
            when(userRepository.findByUsernameNormalized("peterg")).thenReturn(Optional.of(user));

            ProfileResponseDTO profile = profileService.getProfile(VIEWER_ID, "PeterG");

            assertThat(profile.getUsername()).isEqualTo(user.getUsername());
            verify(userRepository).findByUsernameNormalized("peterg");
        }

        @Test
        void should_throw_when_the_user_is_unknown() {
            when(userRepository.findByUsernameNormalized("nobody")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profileService.getProfile(VIEWER_ID, "nobody"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void should_throw_when_the_user_is_unconfirmed() {
            User user = TestEntities.newUser();
            when(userRepository.findByUsernameNormalized("pending")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> profileService.getProfile(VIEWER_ID, "pending"))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        void should_copy_the_stored_counts_into_the_response() {
            User user = confirmedUser();
            user.setFollowersCount(7);
            user.setFollowingCount(3);
            when(userRepository.findByUsernameNormalized("peterg")).thenReturn(Optional.of(user));

            ProfileResponseDTO profile = profileService.getProfile(VIEWER_ID, "PeterG");

            assertThat(profile.getFollowersCount()).isEqualTo(7);
            assertThat(profile.getFollowingCount()).isEqualTo(3);
        }

        @Test
        void should_mark_followed_by_me_when_the_viewer_follows_the_profile() {
            User user = confirmedUser();
            when(userRepository.findByUsernameNormalized("peterg")).thenReturn(Optional.of(user));
            when(followRepository.existsByFollowerIdAndFollowingId(VIEWER_ID, user.getId())).thenReturn(true);

            assertThat(profileService.getProfile(VIEWER_ID, "PeterG").isFollowedByMe()).isTrue();
        }

        @Test
        void should_not_mark_followed_by_me_when_the_viewer_does_not_follow_the_profile() {
            User user = confirmedUser();
            when(userRepository.findByUsernameNormalized("peterg")).thenReturn(Optional.of(user));
            when(followRepository.existsByFollowerIdAndFollowingId(VIEWER_ID, user.getId())).thenReturn(false);

            assertThat(profileService.getProfile(VIEWER_ID, "PeterG").isFollowedByMe()).isFalse();
        }

        @Test
        void should_not_mark_followed_by_me_and_skip_the_query_when_the_viewer_opens_their_own_profile() {
            User user = confirmedUser();
            when(userRepository.findByUsernameNormalized("peterg")).thenReturn(Optional.of(user));

            ProfileResponseDTO profile = profileService.getProfile(user.getId(), "PeterG");

            assertThat(profile.isFollowedByMe()).isFalse();
            verifyNoInteractions(followRepository);
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
        user.setId(UUID.randomUUID());
        user.setUsername("PeterG");
        user.setEnabled(true);

        return user;
    }
}
