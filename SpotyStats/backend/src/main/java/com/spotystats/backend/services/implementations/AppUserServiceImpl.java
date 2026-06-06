package com.spotystats.backend.services.implementations;

import com.spotystats.backend.entities.AppUser;
import com.spotystats.backend.repositories.AppUserRepository;
import com.spotystats.backend.services.interfaces.AppUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class AppUserServiceImpl implements AppUserService {

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public AppUser upsertFromSpotifyUser(OAuth2User spotifyUser) {
        final String spotifyUserId = spotifyUser.getName();

        final AppUser user = appUserRepository
                .findById(spotifyUserId)
                .orElseGet(() -> new AppUser(spotifyUserId));

        user.setDisplayName(spotifyUser.getAttribute("display_name"));
        user.setEmail(spotifyUser.getAttribute("email"));
        user.setImageUrl(firstImageUrl(spotifyUser.getAttribute("images")));

        return appUserRepository.save(user);
    }

    @SuppressWarnings("unchecked")
    private static String firstImageUrl(Object imagesAttribute) {
        if (!(imagesAttribute instanceof List<?> images) || images.isEmpty()) {
            return null;
        }

        final Object first = images.getFirst();
        if (first instanceof Map<?, ?> image) {
            final Object url = ((Map<String, Object>) image).get("url");
            return url != null ? url.toString() : null;
        }

        return null;
    }
}
