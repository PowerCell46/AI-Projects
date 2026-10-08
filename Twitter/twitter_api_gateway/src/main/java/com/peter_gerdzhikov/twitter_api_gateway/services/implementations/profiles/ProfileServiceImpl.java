package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.profiles;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.UpdateProfileRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.profiles.ProfileService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.profiles.ProfileMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;

    private final FollowRepository followRepository;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponseDTO getProfile(UUID viewerId, String username) {
        User user = userRepository
                .findByUsernameNormalized(username.toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(UserNotFoundException::new);

        return ProfileMapper.toResponse(user, isFollowedBy(viewerId, user));
    }

    @Override
    @Transactional
    public ProfileResponseDTO updateProfile(UUID userId, UpdateProfileRequestDTO request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(UserNotFoundException::new);
        applyIfPresent(request.getBio(), user::setBio);
        applyIfPresent(request.getLocation(), user::setLocation);
        applyIfPresent(request.getBirthdate(), user::setBirthdate);

        return ProfileMapper.toResponse(user, false);
    }

    private boolean isFollowedBy(UUID viewerId, User profile) {
        return !profile.getId().equals(viewerId)
                && followRepository.existsByFollowerIdAndFollowingId(viewerId, profile.getId());
    }

    private void applyIfPresent(String value, Consumer<String> setter) {
        if (value != null) {
            setter.accept(blankToNull(value));
        }
    }

    private void applyIfPresent(LocalDate value, Consumer<LocalDate> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
