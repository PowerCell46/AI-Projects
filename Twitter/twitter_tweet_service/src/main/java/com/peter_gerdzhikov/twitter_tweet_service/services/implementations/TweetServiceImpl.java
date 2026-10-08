package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetImageContentResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetPageResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.tweets.TweetSummaryResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.EmptyUploadException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.EmptyTweetException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.NotTweetAuthorException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TooManyImagesException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetIdsOutOfRangeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetImageNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetContentTooLongException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetLimitOutOfRangeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.replies.ReplyRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.ConflictRetrier;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.ObjectStorageService;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.OutboxService;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.TweetService;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.ImageSignatureValidator;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.mappers.TweetMapper;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.paging.KeysetCursor;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.paging.KeysetCursorCodec;
import com.peter_gerdzhikov.twitter_tweet_service.utilities.paging.PageSizeValidator;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TweetServiceImpl implements TweetService {

    private static final int MAX_IDS_PER_READ = 100;

    private static final int MAX_TWEETS_PER_AUTHOR_READ = 100;

    private final Clock clock;

    private final int maxFiles;

    private final long maxFileBytes;

    private final String createdTopic;

    private final String deletedTopic;

    private final int maxContentCodePoints;

    private final OutboxService outboxService;

    private final ReplyRepository replyRepository;

    private final TweetRepository tweetRepository;

    private final ConflictRetrier conflictRetrier;

    private final TransactionTemplate transactionTemplate;

    private final ObjectStorageService objectStorageService;

    public TweetServiceImpl(
            Clock clock,
            @Value("${app.upload.max-files}") int maxFiles,
            @Value("${app.upload.max-file-bytes}") long maxFileBytes,
            @Value("${app.kafka.tweet-created.name}") String createdTopic,
            @Value("${app.kafka.tweet-deleted.name}") String deletedTopic,
            @Value("${app.tweets.max-content-code-points}") int maxContentCodePoints,
            OutboxService outboxService,
            ReplyRepository replyRepository,
            TweetRepository tweetRepository,
            ConflictRetrier conflictRetrier,
            TransactionTemplate transactionTemplate,
            ObjectStorageService objectStorageService
    ) {
        this.clock = clock;
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileBytes;
        this.createdTopic = createdTopic;
        this.deletedTopic = deletedTopic;
        this.maxContentCodePoints = maxContentCodePoints;
        this.outboxService = outboxService;
        this.replyRepository = replyRepository;
        this.tweetRepository = tweetRepository;
        this.conflictRetrier = conflictRetrier;
        this.transactionTemplate = transactionTemplate;
        this.objectStorageService = objectStorageService;
    }

    @Override
    public TweetResponseDTO create(UUID authorId, String content, List<MultipartFile> images) {
        String text = content == null ? "" : content.strip();
        List<MultipartFile> files = images == null ? List.of() : images;
        validateText(text, files.size());
        if (files.size() > maxFiles) {
            throw new TooManyImagesException(maxFiles);
        }

        List<String> contentTypes = detectContentTypes(files);

        List<TweetImage> storedImages = storeAll(files, contentTypes);
        Tweet tweet;
        try {
            tweet = transactionTemplate.execute(status -> saveWithEvent(authorId, text, storedImages));

        } catch (RuntimeException e) {
            deleteObjectsQuietly(storedImages);
            throw e;
        }

        return TweetMapper.toResponse(tweet);
    }

    @Override
    public TweetResponseDTO update(UUID callerId, UUID tweetId, String content) {
        Tweet tweet = tweetRepository
                .findById(tweetId)
                .orElseThrow(TweetNotFoundException::new);
        if (!tweet.getAuthorId().equals(callerId)) {
            throw new NotTweetAuthorException(NotTweetAuthorException.EDIT_MESSAGE);
        }

        String text = content == null ? "" : content.strip();
        validateText(text, tweet.getImages().size());
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (!tweetRepository.updateContentIfAuthor(tweetId, callerId, text, now)) {
            throw new TweetNotFoundException();
        }

        return TweetMapper.toResponse(tweetRepository
                .findById(tweetId)
                .orElseThrow(TweetNotFoundException::new));
    }

    @Override
    public void delete(UUID callerId, UUID tweetId) {
        Tweet tweet = tweetRepository
                .findById(tweetId)
                .orElseThrow(TweetNotFoundException::new);
        if (!tweet.getAuthorId().equals(callerId)) {
            throw new NotTweetAuthorException(NotTweetAuthorException.DELETE_MESSAGE);
        }

        removeWithEvent(tweet);
        deleteObjectsQuietly(tweet.getImages());
    }

    @Override
    public TweetResponseDTO get(UUID tweetId) {
        return tweetRepository
                .findById(tweetId)
                .map(TweetMapper::toResponse)
                .orElseThrow(TweetNotFoundException::new);
    }

    @Override
    public List<TweetResponseDTO> findByIds(List<UUID> ids) {
        if (ids.isEmpty() || ids.size() > MAX_IDS_PER_READ) {
            throw new TweetIdsOutOfRangeException(MAX_IDS_PER_READ);
        }

        return tweetRepository
                .findAllById(new LinkedHashSet<>(ids))
                .stream()
                .map(TweetMapper::toResponse)
                .toList();
    }

    @Override
    public List<TweetSummaryResponseDTO> findNewestByAuthor(UUID authorId, Instant since, int limit) {
        if (limit < 1 || limit > MAX_TWEETS_PER_AUTHOR_READ) {
            throw new TweetLimitOutOfRangeException(MAX_TWEETS_PER_AUTHOR_READ);
        }

        return tweetRepository
                .findNewestByAuthorSince(authorId, since, limit)
                .stream()
                .map(TweetMapper::toSummary)
                .toList();
    }

    @Override
    public TweetPageResponseDTO findPageByAuthor(UUID authorId, String cursor, int size) {
        PageSizeValidator.validate(size);
        KeysetCursor position = cursor == null ? null : KeysetCursorCodec.decode(cursor);

        List<Tweet> fetched = findOneMoreThanThePage(authorId, position, size);
        boolean hasNextPage = fetched.size() > size;
        List<Tweet> page = hasNextPage ? fetched.subList(0, size) : fetched;

        return TweetPageResponseDTO
                .builder()
                .nextCursor(hasNextPage ? cursorAfter(page.getLast()) : null)
                .items(page
                        .stream()
                        .map(TweetMapper::toResponse)
                        .toList())
                .build();
    }

    @Override
    public long countByAuthor(UUID authorId) {
        return tweetRepository.countByAuthorId(authorId);
    }

    @Override
    public TweetImageContentResponseDTO openImage(UUID tweetId, UUID imageId) {
        Tweet tweet = tweetRepository
                .findById(tweetId)
                .orElseThrow(TweetNotFoundException::new);
        TweetImage image = tweet
                .getImages()
                .stream()
                .filter(candidate -> candidate.getId().equals(imageId))
                .findFirst()
                .orElseThrow(TweetImageNotFoundException::new);

        return TweetImageContentResponseDTO
                .builder()
                .sizeBytes(image.getSizeBytes())
                .contentType(image.getContentType())
                .content(objectStorageService.get(image.getObjectKey()))
                .build();
    }

    private List<Tweet> findOneMoreThanThePage(UUID authorId, KeysetCursor position, int size) {
        if (position == null) {
            return tweetRepository.findFirstPageByAuthor(authorId, size + 1);
        }

        return tweetRepository.findPageByAuthorAfter(authorId, position.getCreatedAt(), position.getId(), size + 1);
    }

    private String cursorAfter(Tweet lastOfThePage) {
        return KeysetCursorCodec.encode(lastOfThePage.getCreatedAt(), lastOfThePage.getId());
    }

    /**
     * A write conflict means a concurrent writer touched the tweet. If it is gone, that writer deleted it and
     * this caller lost the race; if it is still there (a concurrent edit or reply), the transaction is tried again.
     */
    private void removeWithEvent(Tweet tweet) {
        conflictRetrier.executeWithoutResult(() -> removeAndEnqueue(tweet), () -> requireTweetStillExists(tweet));
    }

    private void requireTweetStillExists(Tweet tweet) {
        if (!tweetRepository.existsById(tweet.getId())) {
            throw new TweetNotFoundException();
        }
    }

    private void removeAndEnqueue(Tweet tweet) {
        if (!tweetRepository.deleteIfAuthor(tweet.getId(), tweet.getAuthorId())) {
            throw new TweetNotFoundException();
        }

        replyRepository.deleteAllByTweetId(tweet.getId());

        TweetDeletedEventDTO event = TweetDeletedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .tweetId(tweet.getId())
                .authorId(tweet.getAuthorId())
                .deletedAt(Instant.now(clock).truncatedTo(ChronoUnit.MILLIS))
                .build();
        outboxService.enqueue(deletedTopic, tweet.getId().toString(), event);
    }

    private void validateText(String text, int imageCount) {
        if (text.codePointCount(0, text.length()) > maxContentCodePoints) {
            throw new TweetContentTooLongException(maxContentCodePoints);
        }

        if (text.isEmpty() && imageCount == 0) {
            throw new EmptyTweetException();
        }
    }

    private List<String> detectContentTypes(List<MultipartFile> files) {
        return files
                .stream()
                .map(this::detectContentType)
                .toList();
    }

    private String detectContentType(MultipartFile file) {
        if (file.isEmpty()) {
            throw new EmptyUploadException();
        }

        if (file.getSize() > maxFileBytes) {
            throw new MaxUploadSizeExceededException(maxFileBytes);
        }

        try (InputStream content = file.getInputStream()) {
            return ImageSignatureValidator.detectContentType(content.readNBytes(ImageSignatureValidator.HEADER_BYTES));

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<TweetImage> storeAll(List<MultipartFile> files, List<String> contentTypes) {
        List<TweetImage> storedImages = new ArrayList<>();
        try {
            for (int i = 0; i < files.size(); i++) {
                storedImages.add(store(files.get(i), contentTypes.get(i)));
            }

        } catch (RuntimeException e) {
            deleteObjectsQuietly(storedImages);
            throw e;
        }

        return storedImages;
    }

    private TweetImage store(MultipartFile file, String contentType) {
        String objectKey = UUID.randomUUID().toString();
        try (InputStream content = file.getInputStream()) {
            objectStorageService.put(objectKey, content, file.getSize(), contentType);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return TweetImage
                .builder()
                .id(UUID.randomUUID())
                .sizeBytes(file.getSize())
                .objectKey(objectKey)
                .contentType(contentType)
                .build();
    }

    private Tweet saveWithEvent(UUID authorId, String text, List<TweetImage> storedImages) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        Tweet tweet = tweetRepository.insert(Tweet
                .builder()
                .id(UUID.randomUUID())
                .authorId(authorId)
                .content(text)
                .images(storedImages)
                .createdAt(now)
                .updatedAt(now)
                .build());

        outboxService.enqueue(createdTopic, tweet.getId().toString(), createdEvent(tweet));

        return tweet;
    }

    private TweetCreatedEventDTO createdEvent(Tweet tweet) {
        return TweetCreatedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .tweetId(tweet.getId())
                .authorId(tweet.getAuthorId())
                .content(tweet.getContent())
                .createdAt(tweet.getCreatedAt())
                .imageIds(tweet
                        .getImages()
                        .stream()
                        .map(TweetImage::getId)
                        .toList())
                .build();
    }

    private void deleteObjectsQuietly(List<TweetImage> images) {
        images.forEach(image -> deleteObjectQuietly(image.getObjectKey()));
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            objectStorageService.delete(objectKey);

        } catch (RuntimeException e) {
            log.warn("Could not delete object '{}'; it is now orphaned.", objectKey, e);
        }
    }
}
