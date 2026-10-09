package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.follows;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.follows.FollowListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.follows.Follow;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.follows.FollowListService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.follows.FollowCursor;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.follows.FollowCursorCodec;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.profiles.ProfileMapper;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.paging.PageSizeValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FollowListServiceImpl implements FollowListService {

    private final UserRepository userRepository;

    private final FollowRepository followRepository;

    @Override
    @Transactional(readOnly = true)
    public FollowListResponseDTO getFollowers(UUID viewerId, String username, String cursor, int size) {
        return getPage(viewerId, username, cursor, size, this::fetchFollowers, Follow::getFollower);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowListResponseDTO getFollowing(UUID viewerId, String username, String cursor, int size) {
        return getPage(viewerId, username, cursor, size, this::fetchFollowing, Follow::getFollowing);
    }

    private FollowListResponseDTO getPage(
            UUID viewerId,
            String username,
            String cursor,
            int size,
            PageFetcher fetcher,
            Function<Follow, User> memberOf
    ) {
        PageSizeValidator.validate(size);
        FollowCursor decodedCursor = cursor == null ? null : FollowCursorCodec.decode(cursor);
        UUID userId = resolveUserId(username);

        // One row beyond the page tells whether a next page exists.
        List<Follow> rows = fetcher.fetch(userId, decodedCursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<Follow> pageRows = hasNext ? rows.subList(0, size) : rows;

        return FollowListResponseDTO
                .builder()
                .items(toItems(viewerId, pageRows, memberOf))
                .nextCursor(hasNext ? cursorAfter(pageRows.getLast()) : null)
                .build();
    }

    private UUID resolveUserId(String username) {
        return userRepository
                .findByUsernameNormalized(username.toLowerCase(Locale.ROOT))
                .filter(User::isEnabled)
                .map(User::getId)
                .orElseThrow(UserNotFoundException::new);
    }

    private List<Follow> fetchFollowers(UUID userId, FollowCursor cursor, Pageable limit) {
        if (cursor == null) {
            return followRepository.findFollowersFirstPage(userId, limit);
        }

        return followRepository.findFollowersAfter(userId, cursor.getCreatedAt(), cursor.getId(), limit);
    }

    private List<Follow> fetchFollowing(UUID userId, FollowCursor cursor, Pageable limit) {
        if (cursor == null) {
            return followRepository.findFollowingFirstPage(userId, limit);
        }

        return followRepository.findFollowingAfter(userId, cursor.getCreatedAt(), cursor.getId(), limit);
    }

    private List<FollowListItemResponseDTO> toItems(UUID viewerId, List<Follow> rows, Function<Follow, User> memberOf) {
        List<User> members = rows
                .stream()
                .map(memberOf)
                .toList();
        Set<UUID> followedIds = followedByViewer(viewerId, members);

        return members
                .stream()
                .map(member -> ProfileMapper.toListItem(member, followedIds.contains(member.getId())))
                .toList();
    }

    private Set<UUID> followedByViewer(UUID viewerId, List<User> members) {
        if (members.isEmpty()) {
            return Set.of();
        }

        List<UUID> memberIds = members
                .stream()
                .map(User::getId)
                .toList();

        return Set.copyOf(followRepository.findFollowedIds(viewerId, memberIds));
    }

    private String cursorAfter(Follow lastRow) {
        return FollowCursorCodec.encode(lastRow.getCreatedAt(), lastRow.getId());
    }

    @FunctionalInterface
    private interface PageFetcher {

        List<Follow> fetch(UUID userId, FollowCursor cursor, Pageable limit);
    }
}
