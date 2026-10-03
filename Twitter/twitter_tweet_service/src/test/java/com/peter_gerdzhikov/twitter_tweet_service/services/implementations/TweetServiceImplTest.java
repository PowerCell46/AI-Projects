package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import com.peter_gerdzhikov.twitter_tweet_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetImageContentResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.TweetResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.documents.TweetImage;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.EmptyUploadException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.images.UnsupportedImageTypeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.EmptyTweetException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.NotTweetAuthorException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TooManyImagesException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetIdsOutOfRangeException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetImageNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetContentTooLongException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.ObjectStorageService;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.OutboxService;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestImages;

import com.mongodb.MongoException;

@ExtendWith(MockitoExtension.class)
class TweetServiceImplTest {

    private static final int MAX_FILES = 4;

    private static final long MAX_FILE_BYTES = 4096;

    private static final int MAX_CONTENT_CODE_POINTS = 280;

    private static final String CREATED_TOPIC = "tweet.created";

    private static final String DELETED_TOPIC = "tweet.deleted";

    private static final UUID AUTHOR_ID = UUID.randomUUID();

    private static final Instant NOW = Instant.parse("2026-03-04T05:06:07.123456Z");

    private TweetServiceImpl tweetService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        tweetService = new TweetServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC),
                MAX_FILES,
                MAX_FILE_BYTES,
                CREATED_TOPIC,
                DELETED_TOPIC,
                MAX_CONTENT_CODE_POINTS,
                outboxService,
                tweetRepository,
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                objectStorageService
        );
    }

    @Nested
    class Create {

        @Test
        void should_save_the_trimmed_tweet_with_ms_timestamps_and_no_images_when_only_text_is_given() {
            saveEchoesTheTweet();

            TweetResponseDTO response = tweetService.create(AUTHOR_ID, "  hello  ", null);

            ArgumentCaptor<Tweet> saved = ArgumentCaptor.forClass(Tweet.class);
            verify(tweetRepository).insert(saved.capture());
            Instant expectedInstant = Instant.parse("2026-03-04T05:06:07.123Z");
            assertThat(saved.getValue().getContent()).isEqualTo("hello");
            assertThat(saved.getValue().getAuthorId()).isEqualTo(AUTHOR_ID);
            assertThat(saved.getValue().getCreatedAt()).isEqualTo(expectedInstant);
            assertThat(saved.getValue().getUpdatedAt()).isEqualTo(expectedInstant);
            assertThat(response.getImages()).isEmpty();
            verifyNoInteractions(objectStorageService);
        }

        @Test
        void should_enqueue_the_created_event_keyed_by_the_tweet_id_with_the_image_ids_in_order() {
            saveEchoesTheTweet();

            TweetResponseDTO response = tweetService.create(AUTHOR_ID, "hi", List.of(png(), jpeg()));

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).enqueue(eq(CREATED_TOPIC), eq(response.getId().toString()), payload.capture());
            TweetCreatedEventDTO event = (TweetCreatedEventDTO) payload.getValue();
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getTweetId()).isEqualTo(response.getId());
            assertThat(event.getAuthorId()).isEqualTo(AUTHOR_ID);
            assertThat(event.getContent()).isEqualTo("hi");
            assertThat(event.getCreatedAt()).isEqualTo(response.getCreatedAt());
            assertThat(event.getImageIds()).containsExactly(
                    response.getImages().get(0).getId(),
                    response.getImages().get(1).getId());
        }

        @Test
        void should_store_each_image_under_a_fresh_key_with_the_detected_type_when_images_are_given() {
            saveEchoesTheTweet();

            tweetService.create(AUTHOR_ID, "hi", List.of(png(), jpeg(), webp()));

            ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> types = ArgumentCaptor.forClass(String.class);
            verify(objectStorageService, times(3)).put(keys.capture(), any(), anyLong(), types.capture());
            assertThat(types.getAllValues()).containsExactly("image/png", "image/jpeg", "image/webp");
            assertThat(keys.getAllValues()).doesNotHaveDuplicates().allSatisfy(key -> UUID.fromString(key));
        }

        @Test
        void should_accept_an_empty_text_when_an_image_is_attached() {
            saveEchoesTheTweet();

            TweetResponseDTO response = tweetService.create(AUTHOR_ID, "   ", List.of(png()));

            assertThat(response.getContent()).isEmpty();
        }

        @Test
        void should_count_code_points_not_utf16_units_when_the_text_is_all_emoji() {
            saveEchoesTheTweet();

            assertThatCode(() -> tweetService.create(AUTHOR_ID, "😀".repeat(MAX_CONTENT_CODE_POINTS), null))
                    .doesNotThrowAnyException();
        }

        @Test
        void should_reject_the_text_and_touch_nothing_when_it_is_over_the_code_point_limit() {
            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "😀".repeat(MAX_CONTENT_CODE_POINTS + 1), List.of(png())))
                    .isInstanceOf(TweetContentTooLongException.class)
                    .hasMessage("A tweet can be at most 280 characters.");

            verifyNoInteractions(objectStorageService, tweetRepository, outboxService);
        }

        @Test
        void should_reject_the_tweet_when_the_text_is_trimmed_to_nothing_and_there_are_no_images() {
            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, " \t\n ", null))
                    .isInstanceOf(EmptyTweetException.class);

            verifyNoInteractions(objectStorageService, tweetRepository, outboxService);
        }

        @Test
        void should_reject_the_tweet_and_touch_nothing_when_there_are_too_many_images() {
            List<MultipartFile> files = List.of(png(), png(), png(), png(), png());

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", files))
                    .isInstanceOf(TooManyImagesException.class)
                    .hasMessage("A tweet can have at most 4 images.");

            verifyNoInteractions(objectStorageService, tweetRepository, outboxService);
        }

        @Test
        void should_reject_the_tweet_and_touch_nothing_when_an_image_part_is_empty() {
            MultipartFile empty = new MockMultipartFile("images", "a.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", List.of(png(), empty)))
                    .isInstanceOf(EmptyUploadException.class);

            verifyNoInteractions(objectStorageService, tweetRepository, outboxService);
        }

        @Test
        void should_reject_the_tweet_and_touch_nothing_when_an_image_is_over_the_file_limit() {
            byte[] tooBig = new byte[(int) MAX_FILE_BYTES + 1];
            System.arraycopy(TestImages.png(), 0, tooBig, 0, TestImages.png().length);
            MultipartFile file = new MockMultipartFile("images", "a.png", "image/png", tooBig);

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", List.of(png(), file)))
                    .isInstanceOf(MaxUploadSizeExceededException.class);

            verifyNoInteractions(objectStorageService, tweetRepository, outboxService);
        }

        @Test
        void should_reject_the_tweet_before_storing_the_valid_images_when_one_image_is_not_an_image() {
            MultipartFile notAnImage = new MockMultipartFile("images", "a.png", "image/png", TestImages.notAnImage());

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", List.of(png(), jpeg(), notAnImage)))
                    .isInstanceOf(UnsupportedImageTypeException.class);

            verifyNoInteractions(objectStorageService, tweetRepository, outboxService);
        }

        @Test
        void should_delete_the_images_already_stored_and_save_nothing_when_the_third_of_four_puts_fails() {
            List<String> storedKeys = new ArrayList<>();
            doAnswer(call -> {
                if (storedKeys.size() == 2) {
                    throw new StorageUnavailableException(new RuntimeException("down"));
                }

                storedKeys.add(call.getArgument(0));

                return null;
            }).when(objectStorageService).put(anyString(), any(), anyLong(), anyString());

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", List.of(png(), png(), png(), png())))
                    .isInstanceOf(StorageUnavailableException.class);

            assertThat(storedKeys).hasSize(2);
            storedKeys.forEach(key -> verify(objectStorageService).delete(key));
            verify(objectStorageService, times(2)).delete(anyString());
            verifyNoInteractions(tweetRepository, outboxService);
        }

        @Test
        void should_delete_every_stored_image_and_enqueue_nothing_when_the_insert_fails() {
            when(tweetRepository.insert(any(Tweet.class))).thenThrow(new IllegalStateException("mongo down"));

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", List.of(png(), jpeg())))
                    .isInstanceOf(IllegalStateException.class);

            ArgumentCaptor<String> putKeys = ArgumentCaptor.forClass(String.class);
            verify(objectStorageService, times(2)).put(putKeys.capture(), any(), anyLong(), anyString());
            putKeys.getAllValues().forEach(key -> verify(objectStorageService).delete(key));
            verifyNoInteractions(outboxService);
        }

        @Test
        void should_delete_every_stored_image_when_the_outbox_enqueue_fails() {
            saveEchoesTheTweet();
            doThrow(new IllegalStateException("outbox down"))
                    .when(outboxService).enqueue(anyString(), anyString(), any());

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", List.of(png())))
                    .isInstanceOf(IllegalStateException.class);

            verify(objectStorageService).delete(anyString());
        }

        @Test
        void should_rethrow_the_original_failure_when_deleting_a_stored_image_fails_during_cleanup() {
            when(tweetRepository.insert(any(Tweet.class))).thenThrow(new IllegalStateException("mongo down"));
            doThrow(new StorageUnavailableException(new RuntimeException("down")))
                    .when(objectStorageService).delete(anyString());

            assertThatThrownBy(() -> tweetService.create(AUTHOR_ID, "hi", List.of(png())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("mongo down");
        }

        private void saveEchoesTheTweet() {
            when(tweetRepository.insert(any(Tweet.class))).thenAnswer(call -> call.getArgument(0));
        }
    }

    @Nested
    class Delete {

        @Test
        void should_delete_the_tweet_and_enqueue_the_deleted_event_in_one_transaction_when_the_author_deletes() {
            Tweet tweet = tweetWithImages(2);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID)).thenReturn(true);

            tweetService.delete(AUTHOR_ID, tweet.getId());

            ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
            verify(outboxService).enqueue(eq(DELETED_TOPIC), eq(tweet.getId().toString()), payload.capture());
            TweetDeletedEventDTO event = (TweetDeletedEventDTO) payload.getValue();
            assertThat(event.getEventId()).isNotNull();
            assertThat(event.getTweetId()).isEqualTo(tweet.getId());
            assertThat(event.getAuthorId()).isEqualTo(AUTHOR_ID);
            assertThat(event.getDeletedAt()).isEqualTo(Instant.parse("2026-03-04T05:06:07.123Z"));
        }

        @Test
        void should_delete_every_image_object_only_after_the_tweet_is_deleted() {
            Tweet tweet = tweetWithImages(2);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID)).thenReturn(true);

            tweetService.delete(AUTHOR_ID, tweet.getId());

            InOrder order = inOrder(tweetRepository, objectStorageService);
            order.verify(tweetRepository).deleteIfAuthor(tweet.getId(), AUTHOR_ID);
            tweet.getImages().forEach(image -> order.verify(objectStorageService).delete(image.getObjectKey()));
        }

        @Test
        void should_still_succeed_when_deleting_an_image_object_fails_after_the_commit() {
            Tweet tweet = tweetWithImages(2);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID)).thenReturn(true);
            doThrow(new StorageUnavailableException(new RuntimeException("down")))
                    .when(objectStorageService).delete(anyString());

            assertThatCode(() -> tweetService.delete(AUTHOR_ID, tweet.getId())).doesNotThrowAnyException();

            verify(objectStorageService, times(2)).delete(anyString());
        }

        @Test
        void should_throw_not_found_when_the_tweet_does_not_exist() {
            when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tweetService.delete(AUTHOR_ID, UUID.randomUUID()))
                    .isInstanceOf(TweetNotFoundException.class);

            verifyNoInteractions(outboxService, objectStorageService);
        }

        @Test
        void should_throw_forbidden_and_change_nothing_when_the_caller_is_not_the_author() {
            Tweet tweet = tweetWithImages(1);

            assertThatThrownBy(() -> tweetService.delete(UUID.randomUUID(), tweet.getId()))
                    .isInstanceOf(NotTweetAuthorException.class)
                    .hasMessage("You can only delete your own tweets.");

            verify(tweetRepository, times(0)).deleteIfAuthor(any(), any());
            verifyNoInteractions(outboxService, objectStorageService);
        }

        @Test
        void should_throw_not_found_and_enqueue_nothing_when_nothing_was_deleted() {
            Tweet tweet = tweetWithImages(1);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID)).thenReturn(false);

            assertThatThrownBy(() -> tweetService.delete(AUTHOR_ID, tweet.getId()))
                    .isInstanceOf(TweetNotFoundException.class);

            verifyNoInteractions(outboxService, objectStorageService);
        }

        @Test
        void should_keep_the_image_objects_when_the_transaction_fails() {
            Tweet tweet = tweetWithImages(2);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID)).thenReturn(true);
            doThrow(new IllegalStateException("outbox down"))
                    .when(outboxService).enqueue(anyString(), anyString(), any());

            assertThatThrownBy(() -> tweetService.delete(AUTHOR_ID, tweet.getId()))
                    .isInstanceOf(IllegalStateException.class);

            verifyNoInteractions(objectStorageService);
        }

        @Test
        void should_throw_not_found_when_a_write_conflict_is_caused_by_a_concurrent_delete() {
            Tweet tweet = tweetWithImages(1);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID)).thenThrow(new MongoException(112, "conflict"));
            when(tweetRepository.existsById(tweet.getId())).thenReturn(false);

            assertThatThrownBy(() -> tweetService.delete(AUTHOR_ID, tweet.getId()))
                    .isInstanceOf(TweetNotFoundException.class);

            verifyNoInteractions(outboxService, objectStorageService);
        }

        @Test
        void should_retry_the_transaction_when_a_write_conflict_leaves_the_tweet_in_place() {
            Tweet tweet = tweetWithImages(1);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID))
                    .thenThrow(new MongoException(112, "conflict"))
                    .thenReturn(true);
            when(tweetRepository.existsById(tweet.getId())).thenReturn(true);

            tweetService.delete(AUTHOR_ID, tweet.getId());

            verify(tweetRepository, times(2)).deleteIfAuthor(tweet.getId(), AUTHOR_ID);
            verify(outboxService).enqueue(eq(DELETED_TOPIC), eq(tweet.getId().toString()), any());
        }

        @Test
        void should_give_up_and_rethrow_when_the_write_conflict_persists() {
            Tweet tweet = tweetWithImages(1);
            when(tweetRepository.deleteIfAuthor(tweet.getId(), AUTHOR_ID)).thenThrow(new MongoException(112, "conflict"));
            when(tweetRepository.existsById(tweet.getId())).thenReturn(true);

            assertThatThrownBy(() -> tweetService.delete(AUTHOR_ID, tweet.getId()))
                    .isInstanceOf(MongoException.class);

            verify(tweetRepository, times(3)).deleteIfAuthor(tweet.getId(), AUTHOR_ID);
            verifyNoInteractions(objectStorageService);
        }

        private Tweet tweetWithImages(int count) {
            Tweet tweet = TestDocuments.tweetBy(AUTHOR_ID);
            tweet.setImages(TestDocuments.images(count));
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));

            return tweet;
        }
    }

    @Nested
    class Update {

        @Test
        void should_write_the_trimmed_text_with_the_clock_time_and_return_the_reread_tweet_when_the_author_edits() {
            Tweet tweet = TestDocuments.tweetBy(AUTHOR_ID);
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));
            when(tweetRepository.updateContentIfAuthor(any(), any(), anyString(), any())).thenReturn(true);

            TweetResponseDTO response = tweetService.update(AUTHOR_ID, tweet.getId(), "  edited  ");

            verify(tweetRepository).updateContentIfAuthor(
                    tweet.getId(), AUTHOR_ID, "edited", Instant.parse("2026-03-04T05:06:07.123Z"));
            assertThat(response.getId()).isEqualTo(tweet.getId());
            verifyNoInteractions(outboxService, objectStorageService);
        }

        @Test
        void should_throw_not_found_when_the_tweet_does_not_exist() {
            when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tweetService.update(AUTHOR_ID, UUID.randomUUID(), "x"))
                    .isInstanceOf(TweetNotFoundException.class);
        }

        @Test
        void should_throw_forbidden_and_write_nothing_when_the_caller_is_not_the_author() {
            Tweet tweet = TestDocuments.tweetBy(AUTHOR_ID);
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));

            assertThatThrownBy(() -> tweetService.update(UUID.randomUUID(), tweet.getId(), "x"))
                    .isInstanceOf(NotTweetAuthorException.class)
                    .hasMessage("You can only edit your own tweets.");

            verify(tweetRepository, times(0)).updateContentIfAuthor(any(), any(), any(), any());
        }

        @Test
        void should_reject_the_text_when_it_is_over_the_code_point_limit() {
            Tweet tweet = TestDocuments.tweetBy(AUTHOR_ID);
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));

            assertThatThrownBy(() -> tweetService.update(AUTHOR_ID, tweet.getId(), "a".repeat(MAX_CONTENT_CODE_POINTS + 1)))
                    .isInstanceOf(TweetContentTooLongException.class);

            verify(tweetRepository, times(0)).updateContentIfAuthor(any(), any(), any(), any());
        }

        @Test
        void should_reject_blank_text_when_the_tweet_has_no_images() {
            Tweet tweet = TestDocuments.tweetBy(AUTHOR_ID);
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));

            assertThatThrownBy(() -> tweetService.update(AUTHOR_ID, tweet.getId(), "   "))
                    .isInstanceOf(EmptyTweetException.class);
        }

        @Test
        void should_accept_blank_text_when_the_tweet_has_images() {
            Tweet tweet = TestDocuments.tweetBy(AUTHOR_ID);
            tweet.setImages(TestDocuments.images(1));
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));
            when(tweetRepository.updateContentIfAuthor(any(), any(), anyString(), any())).thenReturn(true);

            tweetService.update(AUTHOR_ID, tweet.getId(), null);

            verify(tweetRepository).updateContentIfAuthor(eq(tweet.getId()), eq(AUTHOR_ID), eq(""), any());
        }

        @Test
        void should_throw_not_found_when_the_tweet_was_deleted_between_the_load_and_the_write() {
            Tweet tweet = TestDocuments.tweetBy(AUTHOR_ID);
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));
            when(tweetRepository.updateContentIfAuthor(any(), any(), anyString(), any())).thenReturn(false);

            assertThatThrownBy(() -> tweetService.update(AUTHOR_ID, tweet.getId(), "x"))
                    .isInstanceOf(TweetNotFoundException.class);
        }
    }

    @Nested
    class Get {

        @Test
        void should_return_the_tweet_when_it_exists() {
            Tweet tweet = TestDocuments.tweet();
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));

            TweetResponseDTO response = tweetService.get(tweet.getId());

            assertThat(response.getId()).isEqualTo(tweet.getId());
            assertThat(response.getContent()).isEqualTo(tweet.getContent());
        }

        @Test
        void should_throw_not_found_when_the_tweet_does_not_exist() {
            UUID id = UUID.randomUUID();
            when(tweetRepository.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tweetService.get(id)).isInstanceOf(TweetNotFoundException.class);
        }
    }

    @Nested
    class FindByIds {

        @Test
        void should_return_the_tweets_the_repository_found_when_ids_are_given() {
            Tweet first = TestDocuments.tweet();
            Tweet second = TestDocuments.tweet();
            when(tweetRepository.findAllById(any())).thenReturn(List.of(first, second));

            List<TweetResponseDTO> response = tweetService.findByIds(List.of(first.getId(), second.getId()));

            assertThat(response)
                    .extracting(TweetResponseDTO::getId)
                    .containsExactly(first.getId(), second.getId());
        }

        @Test
        void should_query_each_id_once_when_ids_repeat() {
            UUID id = UUID.randomUUID();
            UUID other = UUID.randomUUID();
            when(tweetRepository.findAllById(any())).thenReturn(List.of());

            tweetService.findByIds(List.of(id, other, id));

            ArgumentCaptor<Iterable<UUID>> queried = ArgumentCaptor.captor();
            verify(tweetRepository).findAllById(queried.capture());
            assertThat(queried.getValue()).containsExactly(id, other);
        }

        @Test
        void should_accept_exactly_100_ids() {
            when(tweetRepository.findAllById(any())).thenReturn(List.of());

            assertThatCode(() -> tweetService.findByIds(randomIds(100))).doesNotThrowAnyException();
        }

        @Test
        void should_throw_out_of_range_and_not_query_when_no_ids_are_given() {
            assertThatThrownBy(() -> tweetService.findByIds(List.of()))
                    .isInstanceOf(TweetIdsOutOfRangeException.class)
                    .hasMessage("Provide between 1 and 100 tweet ids.");
            verifyNoInteractions(tweetRepository);
        }

        @Test
        void should_throw_out_of_range_and_not_query_when_101_ids_are_given() {
            assertThatThrownBy(() -> tweetService.findByIds(randomIds(101)))
                    .isInstanceOf(TweetIdsOutOfRangeException.class);
            verifyNoInteractions(tweetRepository);
        }

        private List<UUID> randomIds(int count) {
            return Stream
                    .generate(UUID::randomUUID)
                    .limit(count)
                    .toList();
        }
    }

    @Nested
    class OpenImage {

        @Test
        void should_stream_the_stored_object_with_its_type_and_size_when_the_image_belongs_to_the_tweet() {
            Tweet tweet = TestDocuments.tweet();
            TweetImage image = TestDocuments.image();
            tweet.setImages(List.of(image));
            InputStream stream = new ByteArrayInputStream(new byte[]{1});
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));
            when(objectStorageService.get(image.getObjectKey())).thenReturn(stream);

            TweetImageContentResponseDTO response = tweetService.openImage(tweet.getId(), image.getId());

            assertThat(response.getContent()).isSameAs(stream);
            assertThat(response.getContentType()).isEqualTo(image.getContentType());
            assertThat(response.getSizeBytes()).isEqualTo(image.getSizeBytes());
        }

        @Test
        void should_throw_not_found_when_the_tweet_does_not_exist() {
            when(tweetRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> tweetService.openImage(UUID.randomUUID(), UUID.randomUUID()))
                    .isInstanceOf(TweetNotFoundException.class);

            verifyNoInteractions(objectStorageService);
        }

        @Test
        void should_throw_image_not_found_when_the_image_is_not_on_the_tweet() {
            Tweet tweet = TestDocuments.tweet();
            tweet.setImages(TestDocuments.images(2));
            when(tweetRepository.findById(tweet.getId())).thenReturn(Optional.of(tweet));

            assertThatThrownBy(() -> tweetService.openImage(tweet.getId(), UUID.randomUUID()))
                    .isInstanceOf(TweetImageNotFoundException.class);

            verifyNoInteractions(objectStorageService);
        }
    }

    private static MultipartFile png() {
        return new MockMultipartFile("images", "a.png", "image/png", TestImages.png());
    }

    private static MultipartFile jpeg() {
        return new MockMultipartFile("images", "a.jpg", "image/jpeg", TestImages.jpeg());
    }

    private static MultipartFile webp() {
        return new MockMultipartFile("images", "a.webp", "image/webp", TestImages.webp());
    }
}
