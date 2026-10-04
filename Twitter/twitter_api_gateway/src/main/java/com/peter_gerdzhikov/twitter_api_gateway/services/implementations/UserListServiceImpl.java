package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListItemResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.users.UserListResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.FollowRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.UserListService;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.FollowCursor;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.FollowCursorCodec;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.ProfileMapper;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.paging.PageSizeValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserListServiceImpl implements UserListService {

    private final UserRepository userRepository;

    private final FollowRepository followRepository;

    @Override
    @Transactional(readOnly = true)
    public UserListResponseDTO listUsers(UUID callerId, String cursor, int size) {
        PageSizeValidator.validate(size);
        FollowCursor decodedCursor = cursor == null ? null : FollowCursorCodec.decode(cursor);

        // One row beyond the page tells whether a next page exists.
        List<User> rows = fetch(callerId, decodedCursor, PageRequest.of(0, size + 1));
        boolean hasNext = rows.size() > size;
        List<User> pageRows = hasNext ? rows.subList(0, size) : rows;

        return UserListResponseDTO.builder()
                .items(toItems(callerId, pageRows))
                .nextCursor(hasNext ? cursorAfter(pageRows.getLast()) : null)
                .build();
    }

    private List<User> fetch(UUID callerId, FollowCursor cursor, Pageable limit) {
        if (cursor == null) {
            return userRepository.findUserListFirstPage(callerId, limit);
        }

        return userRepository.findUserListAfter(callerId, cursor.getCreatedAt(), cursor.getId(), limit);
    }

    private List<UserListItemResponseDTO> toItems(UUID callerId, List<User> users) {
        Set<UUID> followedIds = followedByCaller(callerId, users);

        return users
                .stream()
                .map(user -> ProfileMapper.toUserListItem(user, followedIds.contains(user.getId())))
                .toList();
    }

    private Set<UUID> followedByCaller(UUID callerId, List<User> users) {
        if (users.isEmpty()) {
            return Set.of();
        }

        List<UUID> userIds = users
                .stream()
                .map(User::getId)
                .toList();

        return Set.copyOf(followRepository.findFollowedIds(callerId, userIds));
    }

    private String cursorAfter(User lastRow) {
        return FollowCursorCodec.encode(lastRow.getCreatedAt(), lastRow.getId());
    }
}
