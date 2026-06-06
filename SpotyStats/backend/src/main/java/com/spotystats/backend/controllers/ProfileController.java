package com.spotystats.backend.controllers;

import com.spotystats.backend.DTOs.profile.ProfileResponse;
import com.spotystats.backend.services.interfaces.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping("/api/profile")
    public ProfileResponse profile() {
        return profileService.currentProfile();
    }
}
