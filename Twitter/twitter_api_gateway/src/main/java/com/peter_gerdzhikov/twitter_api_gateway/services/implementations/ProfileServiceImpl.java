package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.UpdateProfileRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ProfileService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.ProfileMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ProfileResponseDTO getProfile(String username) {
        User user = userRepository
                .findByUsernameNormalized(username.toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .orElseThrow(UserNotFoundException::new);

        return ProfileMapper.toResponse(user);
    }

    @Override
    @Transactional
    public ProfileResponseDTO updateProfile(UUID userId, UpdateProfileRequestDTO request) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(UserNotFoundException::new);
        user.setBio(blankToNull(request.getBio()));
        user.setLocation(blankToNull(request.getLocation()));
        user.setBirthdate(request.getBirthdate());

        return ProfileMapper.toResponse(user);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }
}
