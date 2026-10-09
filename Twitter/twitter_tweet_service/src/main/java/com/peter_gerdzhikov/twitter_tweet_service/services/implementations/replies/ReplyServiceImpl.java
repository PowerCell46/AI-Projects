package com.peter_gerdzhikov.twitter_tweet_service.services.implementations.replies;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyPageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.CallerUnknownException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.EmptyReplyException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyContentTooLongException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.access.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.replies.ReplyRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.ConflictRetrier;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.replies.ReplyService;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.mappers.ReplyMapper;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.paging.PageSizeValidator;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.paging.KeysetCursor;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.paging.KeysetCursorCodec;

@Service
public class ReplyServiceImpl implements ReplyService {

    private final Clock clock;

    private final int maxContentCodePoints;

    private final ConflictRetrier conflictRetrier;

    private final ReplyRepository replyRepository;

    private final TweetRepository tweetRepository;

    private final UserLookupService userLookupService;

    public ReplyServiceImpl(
            Clock clock,
            @Value("${app.tweets.max-content-code-points}") int maxContentCodePoints,
            ConflictRetrier conflictRetrier,
            ReplyRepository replyRepository,
            TweetRepository tweetRepository,
            UserLookupService userLookupService
    ) {
        this.clock = clock;
        this.maxContentCodePoints = maxContentCodePoints;
        this.conflictRetrier = conflictRetrier;
        this.replyRepository = replyRepository;
        this.tweetRepository = tweetRepository;
        this.userLookupService = userLookupService;
    }

    @Override
    public ReplyResponseDTO create(UUID callerId, UUID tweetId, String content) {
        String text = content == null ? "" : content.strip();
        validateText(text);
        UserClientDTO caller = findCaller(callerId);

        Reply reply = conflictRetrier.execute(() -> insertAndCount(callerId, tweetId, text));

        return ReplyMapper.toResponse(reply, caller);
    }

    @Override
    public ReplyPageResponseDTO list(UUID tweetId, String cursor, int size) {
        PageSizeValidator.validate(size);
        KeysetCursor position = cursor == null ? null : KeysetCursorCodec.decode(cursor);
        if (!tweetRepository.existsById(tweetId)) {
            throw new TweetNotFoundException();
        }

        List<Reply> fetched = findOneMoreThanThePage(tweetId, position, size);
        boolean hasNextPage = fetched.size() > size;
        List<Reply> page = hasNextPage ? fetched.subList(0, size) : fetched;
        Map<UUID, UserClientDTO> authors = userLookupService.findByIds(authorIdsOf(page));

        return ReplyPageResponseDTO
                .builder()
                .nextCursor(hasNextPage ? cursorAfter(page.getLast()) : null)
                .items(page
                        .stream()
                        .filter(reply -> authors.containsKey(reply.getAuthorId()))
                        .map(reply -> ReplyMapper.toResponse(reply, authors.get(reply.getAuthorId())))
                        .toList())
                .build();
    }

    @Override
    public ReplyResponseDTO update(UUID callerId, UUID tweetId, UUID replyId, String content) {
        String text = content == null ? "" : content.strip();
        validateText(text);
        UserClientDTO caller = findCaller(callerId);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);

        if (!replyRepository.updateContentIfAuthor(replyId, tweetId, callerId, text, now)) {
            throw new ReplyNotFoundException();
        }

        Reply updated = replyRepository
                .findById(replyId)
                .orElseThrow(ReplyNotFoundException::new);

        return ReplyMapper.toResponse(updated, caller);
    }

    @Override
    public void delete(UUID callerId, UUID tweetId, UUID replyId) {
        Reply reply = findReplyOfTweet(tweetId, replyId);
        Tweet tweet = tweetRepository
                .findById(tweetId)
                .orElseThrow(TweetNotFoundException::new);
        if (!callerId.equals(reply.getAuthorId()) && !callerId.equals(tweet.getAuthorId())) {
            throw new ReplyNotFoundException();
        }

        conflictRetrier.executeWithoutResult(
                () -> removeAndCount(tweetId, replyId),
                () -> requireReplyStillExists(replyId)
        );
    }

    private UserClientDTO findCaller(UUID callerId) {
        UserClientDTO caller = userLookupService
                .findByIds(List.of(callerId))
                .get(callerId);
        if (caller == null) {
            throw new CallerUnknownException();
        }

        return caller;
    }

    private Reply insertAndCount(UUID callerId, UUID tweetId, String text) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        Reply reply = replyRepository.insert(Reply
                .builder()
                .id(UUID.randomUUID())
                .tweetId(tweetId)
                .authorId(callerId)
                .content(text)
                .createdAt(now)
                .updatedAt(now)
                .build());

        if (!tweetRepository.incrementReplyCount(tweetId, 1)) {
            throw new TweetNotFoundException();
        }

        return reply;
    }

    private void removeAndCount(UUID tweetId, UUID replyId) {
        if (!replyRepository.deleteByIdAndTweetId(replyId, tweetId)) {
            throw new ReplyNotFoundException();
        }

        if (!tweetRepository.incrementReplyCount(tweetId, -1)) {
            throw new TweetNotFoundException();
        }
    }

    private void requireReplyStillExists(UUID replyId) {
        if (!replyRepository.existsById(replyId)) {
            throw new ReplyNotFoundException();
        }
    }

    private Reply findReplyOfTweet(UUID tweetId, UUID replyId) {
        return replyRepository
                .findById(replyId)
                .filter(reply -> reply.getTweetId().equals(tweetId))
                .orElseThrow(ReplyNotFoundException::new);
    }

    private List<Reply> findOneMoreThanThePage(UUID tweetId, KeysetCursor position, int size) {
        if (position == null) {
            return replyRepository.findFirstPage(tweetId, size + 1);
        }

        return replyRepository.findPageAfter(tweetId, position.getCreatedAt(), position.getId(), size + 1);
    }

    private Set<UUID> authorIdsOf(List<Reply> replies) {
        return replies
                .stream()
                .map(Reply::getAuthorId)
                .collect(Collectors.toSet());
    }

    private String cursorAfter(Reply lastOfThePage) {
        return KeysetCursorCodec.encode(lastOfThePage.getCreatedAt(), lastOfThePage.getId());
    }

    private void validateText(String text) {
        if (text.codePointCount(0, text.length()) > maxContentCodePoints) {
            throw new ReplyContentTooLongException(maxContentCodePoints);
        }

        if (text.isEmpty()) {
            throw new EmptyReplyException();
        }
    }
}
