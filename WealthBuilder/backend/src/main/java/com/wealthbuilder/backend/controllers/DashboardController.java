package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.dashboard.AssetDistributionResponse;
import com.wealthbuilder.backend.services.interfaces.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


/**
 * Read-only dashboard data for the authenticated user's home screen.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/distribution")
    public List<AssetDistributionResponse> distribution(Authentication authentication) {
        return dashboardService.distribution(authentication.getName());
    }
}
