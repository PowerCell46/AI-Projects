package com.wealthbuilder.backend.services.implementations;

import com.wealthbuilder.backend.DTOs.dashboard.AssetDistributionResponse;
import com.wealthbuilder.backend.entities.User;
import com.wealthbuilder.backend.repositories.HoldingRepository;
import com.wealthbuilder.backend.repositories.UserRepository;
import com.wealthbuilder.backend.services.interfaces.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Default {@link DashboardService}. Delegates the heavy lifting to a single grouped query and
 * maps the projections into the SPA-facing distribution DTO.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final HoldingRepository holdingRepository;

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AssetDistributionResponse> distribution(String username) {
        final User user = requireUser(username);

        return holdingRepository
                .sumInvestedPerAssetByUser(user)
                .stream()
                .map(AssetDistributionResponse::from)
                .toList();
    }

    private User requireUser(String username) {
        return userRepository
                .findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user: " + username));
    }
}
