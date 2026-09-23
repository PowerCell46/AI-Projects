package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.ExistingInterestTopicsRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.InterestTopicFeedRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.ExistingInterestTopicsResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.InterestTopicFeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.InterestTopicFeedUnavailableException;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.InterestTopicLookupFailedException;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InterestTopicLookupService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InterestTopicLookupServiceImpl implements InterestTopicLookupService {

    private static final String EXISTING_PATH = "/internal/v1/interest-topics/existing";

    private static final String FEED_PATH = "/internal/v1/interest-topics/feed";

    private final RestClient interestTopicServiceRestClient;

    @Override
    public Set<UUID> findExistingIds(Collection<UUID> interestTopicIds) {
        ExistingInterestTopicsResponseDTO response = requestExistingIds(interestTopicIds);
        if (response == null || response.getExistingIds() == null) {
            throw new InterestTopicLookupFailedException();
        }

        return Set.copyOf(response.getExistingIds());
    }

    @Override
    public InterestTopicFeedResponseDTO findFeedPage(InterestTopicFeedRequestDTO request) {
        InterestTopicFeedResponseDTO response = requestFeedPage(request);
        if (response == null || response.getItems() == null) {
            throw new InterestTopicFeedUnavailableException();
        }

        return response;
    }

    private ExistingInterestTopicsResponseDTO requestExistingIds(Collection<UUID> interestTopicIds) {
        try {
            return interestTopicServiceRestClient
                    .post()
                    .uri(EXISTING_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ExistingInterestTopicsRequestDTO(List.copyOf(interestTopicIds)))
                    .retrieve()
                    .body(ExistingInterestTopicsResponseDTO.class);

        } catch (RestClientException e) {
            // Non-2xx, a timeout, a refused connection and an unreadable body all land here.
            throw new InterestTopicLookupFailedException(e);
        }
    }

    private InterestTopicFeedResponseDTO requestFeedPage(InterestTopicFeedRequestDTO request) {
        try {
            return interestTopicServiceRestClient
                    .post()
                    .uri(FEED_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(InterestTopicFeedResponseDTO.class);

        } catch (RestClientException e) {
            throw new InterestTopicFeedUnavailableException(e);
        }
    }
}
