package com.spotystats.backend.controllers;

import com.spotystats.backend.dtos.profile.ProfileResponse;
import com.spotystats.backend.services.interfaces.ProfileService;
import com.spotystats.backend.utilities.ZoneIdParser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/api/profile")
    public ProfileResponse profile(
            Authentication authentication,
            @RequestParam(defaultValue = "UTC") String zone) {

        return profileService.currentProfile(authentication.getName(), ZoneIdParser.parseOrUtc(zone));
    }
}
