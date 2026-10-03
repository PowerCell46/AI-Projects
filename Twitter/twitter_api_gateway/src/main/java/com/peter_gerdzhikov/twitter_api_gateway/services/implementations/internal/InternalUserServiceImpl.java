package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.internal;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.FollowerIdsResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.internal.InternalUserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.follows.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserIdsOutOfRangeException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.projections.FollowerEdge;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.internal.InternalUserService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.FollowCursor;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.FollowCursorCodec;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.ProfileMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InternalUserServiceImpl implements InternalUserService {

    public static final int MIN_FOLLOWER_PAGE_SIZE = 1;

    public static final int MAX_FOLLOWER_PAGE_SIZE = 1000;

    public static final int MAX_USER_IDS = 100;

    private final UserRepository userRepository;

    private final FollowRepository followRepository;

    @Override
    @Transactional(readOnly = true)
    public FollowerIdsResponseDTO getFollowerIds(UUID userId, String cursor, int size) {
        validateSize(size);
        FollowCursor decodedCursor = cursor == null ? null : FollowCursorCodec.decode(cursor);

        // One row beyond the page tells whether a next page exists.
        List<FollowerEdge> rows = fetchFollowerEdges(userId, decodedCursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<FollowerEdge> pageRows = hasNext ? rows.subList(0, size) : rows;

        return FollowerIdsResponseDTO
                .builder()
                .ids(pageRows
                        .stream()
                        .map(FollowerEdge::getFollowerId)
                        .toList())
                .nextCursor(hasNext ? cursorAfter(pageRows.getLast()) : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InternalUserResponseDTO> getUsers(List<UUID> ids) {
        if (ids.isEmpty() || ids.size() > MAX_USER_IDS) {
            throw new UserIdsOutOfRangeException(MAX_USER_IDS);
        }

        return userRepository
                .findAllById(new LinkedHashSet<>(ids))
                .stream()
                .filter(User::isEnabled)
                .map(ProfileMapper::toInternalUser)
                .toList();
    }

    private void validateSize(int size) {
        if (size < MIN_FOLLOWER_PAGE_SIZE || size > MAX_FOLLOWER_PAGE_SIZE) {
            throw new InvalidPageSizeException(MIN_FOLLOWER_PAGE_SIZE, MAX_FOLLOWER_PAGE_SIZE);
        }
    }

    private List<FollowerEdge> fetchFollowerEdges(UUID userId, FollowCursor cursor, Pageable limit) {
        if (cursor == null) {
            return followRepository.findFollowerEdgesFirstPage(userId, limit);
        }

        return followRepository.findFollowerEdgesAfter(userId, cursor.getCreatedAt(), cursor.getId(), limit);
    }

    private String cursorAfter(FollowerEdge lastRow) {
        return FollowCursorCodec.encode(lastRow.getCreatedAt(), lastRow.getFollowId());
    }
}
