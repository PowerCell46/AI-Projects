package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.PageResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingFilter;
import com.wealthbuilder.backend.DTOs.holding.HoldingRequest;
import com.wealthbuilder.backend.DTOs.holding.HoldingResponse;
import com.wealthbuilder.backend.DTOs.holding.HoldingSummaryResponse;
import com.wealthbuilder.backend.services.interfaces.HoldingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;


/**
 * Holding endpoints, all scoped to the authenticated caller. Reads and creates are nested
 * under an asset; edit and delete address a holding directly by id. Ownership is enforced in
 * the service (403 if the holding belongs to someone else).
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HoldingController {

    private final HoldingService holdingService;

    @GetMapping("/assets/{assetId}/holdings")
    public PageResponse<HoldingResponse> list(
            @PathVariable Long assetId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        final HoldingFilter filter = HoldingFilter.of(name, from, to);

        return holdingService.listHoldings(authentication.getName(), assetId, filter, pageable);
    }

    @GetMapping("/assets/{assetId}/holdings/summary")
    public HoldingSummaryResponse summary(
            @PathVariable Long assetId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        final HoldingFilter filter = HoldingFilter.of(name, from, to);

        return holdingService.summarize(authentication.getName(), assetId, filter);
    }

    @PostMapping("/assets/{assetId}/holdings")
    @ResponseStatus(HttpStatus.CREATED)
    public HoldingResponse create(
            @PathVariable Long assetId,
            @Valid @RequestBody HoldingRequest request,
            Authentication authentication) {
        return holdingService.create(authentication.getName(), assetId, request);
    }

    @PutMapping("/holdings/{id}")
    public HoldingResponse update(
            @PathVariable Long id,
            @Valid @RequestBody HoldingRequest request,
            Authentication authentication) {
        return holdingService.update(authentication.getName(), id, request);
    }

    @DeleteMapping("/holdings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication authentication) {
        holdingService.delete(authentication.getName(), id);
    }
}
