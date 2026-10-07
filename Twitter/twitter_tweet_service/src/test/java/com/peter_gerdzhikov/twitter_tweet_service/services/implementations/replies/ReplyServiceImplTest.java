package com.peter_gerdzhikov.twitter_tweet_service.services.implementations.replies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mongodb.MongoException;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.client.UserClientDTO;
import com.peter_gerdzhikov.twitter_tweet_service.DTOs.response.replies.ReplyResponseDTO;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Reply;
import com.peter_gerdzhikov.twitter_tweet_service.documents.Tweet;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.CallerUnknownException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.EmptyReplyException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyContentTooLongException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.replies.ReplyNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.tweets.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_tweet_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.replies.ReplyRepository;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.tweets.TweetRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.implementations.ConflictRetrierImpl;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

@ExtendWith(MockitoExtension.class)
class ReplyServiceImplTest {

    private static final int MAX_CONTENT_CODE_POINTS = 280;

    private static final UUID CALLER_ID = UUID.randomUUID();

    private static final Instant NOW = Instant.parse("2026-03-04T05:06:07.123456Z");

    private ReplyServiceImpl replyService;

    private PlatformTransactionManager transactionManager;

    @Mock
    private ReplyRepository replyRepository;

    @Mock
    private TweetRepository tweetRepository;

    @Mock
    private UserLookupService userLookupService;

    @BeforeEach
    void setUp() {
        AtomicLong nanoTime = new AtomicLong();
        transactionManager = mock(PlatformTransactionManager.class);
        replyService = new ReplyServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC),
                MAX_CONTENT_CODE_POINTS,
                new ConflictRetrierImpl(
                        pause -> nanoTime.addAndGet(pause.toNanos()),
                        nanoTime::get,
                        new TransactionTemplate(transactionManager)
                ),
                replyRepository,
                tweetRepository,
                userLookupService
        );
    }

    private void callerIsKnown() {
        when(userLookupService.findByIds(List.of(CALLER_ID)))
                .thenReturn(Map.of(CALLER_ID, UserClientDTO
                        .builder()
                        .id(CALLER_ID)
                        .username("ana")
                        .build()));
    }

    @Nested
    class Create {

        @Test
        void should_look_up_the_caller_before_any_write_when_a_reply_is_created() {
            UUID tweetId = UUID.randomUUID();
            callerIsKnown();
            insertEchoesTheReply();
            when(tweetRepository.incrementReplyCount(tweetId, 1)).thenReturn(true);

            replyService.create(CALLER_ID, tweetId, "hello");

            InOrder order = inOrder(userLookupService, replyRepository, tweetRepository);
            order.verify(userLookupService).findByIds(List.of(CALLER_ID));
            order.verify(replyRepository).insert(any(Reply.class));
            order.verify(tweetRepository).incrementReplyCount(tweetId, 1);
        }

        @Test
        void should_write_nothing_when_the_caller_lookup_fails_on_create() {
            when(userLookupService.findByIds(anyCollection())).thenThrow(new UpstreamUnavailableException(new RuntimeException()));

            assertThatThrownBy(() -> replyService.create(CALLER_ID, UUID.randomUUID(), "hello"))
                    .isInstanceOf(UpstreamUnavailableException.class);

            verifyNoInteractions(replyRepository, tweetRepository);
        }

        @Test
        void should_throw_caller_unknown_when_the_lookup_returns_no_user_on_create() {
            when(userLookupService.findByIds(anyCollection())).thenReturn(Map.of());

            assertThatThrownBy(() -> replyService.create(CALLER_ID, UUID.randomUUID(), "hello"))
                    .isInstanceOf(CallerUnknownException.class);

            verifyNoInteractions(replyRepository, tweetRepository);
        }

        @Test
        void should_save_nothing_when_the_tweet_is_missing() {
            UUID tweetId = UUID.randomUUID();
            callerIsKnown();
            insertEchoesTheReply();
            when(tweetRepository.incrementReplyCount(tweetId, 1)).thenReturn(false);

            assertThatThrownBy(() -> replyService.create(CALLER_ID, tweetId, "hello"))
                    .isInstanceOf(TweetNotFoundException.class);

            verify(transactionManager).rollback(any());
            verify(transactionManager, never()).commit(any());
        }

        @Test
        void should_increment_the_count_only_when_the_reply_is_added() {
            UUID tweetId = UUID.randomUUID();
            callerIsKnown();
            when(replyRepository.insert(any(Reply.class))).thenThrow(new IllegalStateException("mongo down"));

            assertThatThrownBy(() -> replyService.create(CALLER_ID, tweetId, "hello"))
                    .isInstanceOf(IllegalStateException.class);

            verify(tweetRepository, never()).incrementReplyCount(any(), anyLong());
        }

        @Test
        void should_store_the_trimmed_text_with_the_clock_time_and_return_the_reply_with_its_author_when_the_reply_is_created() {
            UUID tweetId = UUID.randomUUID();
            callerIsKnown();
            insertEchoesTheReply();
            when(tweetRepository.incrementReplyCount(tweetId, 1)).thenReturn(true);

            ReplyResponseDTO response = replyService.create(CALLER_ID, tweetId, "  hello  ");

            ArgumentCaptor<Reply> saved = ArgumentCaptor.forClass(Reply.class);
            verify(replyRepository).insert(saved.capture());
            assertThat(saved.getValue().getContent()).isEqualTo("hello");
            assertThat(saved.getValue().getAuthorId()).isEqualTo(CALLER_ID);
            assertThat(saved.getValue().getTweetId()).isEqualTo(tweetId);
            assertThat(saved.getValue().isEdited()).isFalse();
            assertThat(saved.getValue().getCreatedAt()).isEqualTo(Instant.parse("2026-03-04T05:06:07.123Z"));
            assertThat(saved.getValue().getUpdatedAt()).isEqualTo(saved.getValue().getCreatedAt());
            assertThat(response.getAuthor().getUsername()).isEqualTo("ana");
            assertThat(response.getContent()).isEqualTo("hello");
        }

        @Test
        void should_throw_empty_and_look_nothing_up_when_the_text_is_blank() {
            assertThatThrownBy(() -> replyService.create(CALLER_ID, UUID.randomUUID(), "   "))
                    .isInstanceOf(EmptyReplyException.class);

            verifyNoInteractions(userLookupService, replyRepository, tweetRepository);
        }

        @Test
        void should_throw_too_long_and_look_nothing_up_when_the_text_is_281_code_points() {
            assertThatThrownBy(() -> replyService.create(CALLER_ID, UUID.randomUUID(), "a".repeat(281)))
                    .isInstanceOf(ReplyContentTooLongException.class)
                    .hasMessage("A reply can be at most 280 characters.");

            verifyNoInteractions(userLookupService, replyRepository, tweetRepository);
        }

        private void insertEchoesTheReply() {
            when(replyRepository.insert(any(Reply.class))).thenAnswer(call -> call.getArgument(0));
        }
    }

    @Nested
    class Edit {

        @Test
        void should_look_up_the_caller_before_any_write_when_a_reply_is_edited() {
            Reply reply = ownReply();
            callerIsKnown();
            when(replyRepository.updateContentIfAuthor(eq(reply.getId()), eq(reply.getTweetId()), eq(CALLER_ID), anyString(), any()))
                    .thenReturn(true);
            when(replyRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

            replyService.update(CALLER_ID, reply.getTweetId(), reply.getId(), "changed");

            InOrder order = inOrder(userLookupService, replyRepository);
            order.verify(userLookupService).findByIds(List.of(CALLER_ID));
            order.verify(replyRepository).updateContentIfAuthor(eq(reply.getId()), eq(reply.getTweetId()), eq(CALLER_ID), anyString(), any());
        }

        @Test
        void should_write_nothing_when_the_caller_lookup_fails_on_edit() {
            when(userLookupService.findByIds(anyCollection())).thenThrow(new UpstreamUnavailableException(new RuntimeException()));

            assertThatThrownBy(() -> replyService.update(CALLER_ID, UUID.randomUUID(), UUID.randomUUID(), "changed"))
                    .isInstanceOf(UpstreamUnavailableException.class);

            verifyNoInteractions(replyRepository);
        }

        @Test
        void should_throw_caller_unknown_when_the_lookup_returns_no_user_on_edit() {
            when(userLookupService.findByIds(anyCollection())).thenReturn(Map.of());

            assertThatThrownBy(() -> replyService.update(CALLER_ID, UUID.randomUUID(), UUID.randomUUID(), "changed"))
                    .isInstanceOf(CallerUnknownException.class);

            verifyNoInteractions(replyRepository);
        }

        @Test
        void should_throw_reply_not_found_when_someone_else_edits() {
            Reply someoneElses = TestDocuments.replyOn(UUID.randomUUID());
            callerIsKnown();
            when(replyRepository.updateContentIfAuthor(any(), any(), any(), anyString(), any())).thenReturn(false);

            assertThatThrownBy(() -> replyService.update(CALLER_ID, someoneElses.getTweetId(), someoneElses.getId(), "changed"))
                    .isInstanceOf(ReplyNotFoundException.class);
        }

        @Test
        void should_throw_reply_not_found_when_the_reply_is_gone() {
            callerIsKnown();
            when(replyRepository.updateContentIfAuthor(any(), any(), any(), anyString(), any())).thenReturn(false);

            assertThatThrownBy(() -> replyService.update(CALLER_ID, UUID.randomUUID(), UUID.randomUUID(), "changed"))
                    .isInstanceOf(ReplyNotFoundException.class);
        }

        @Test
        void should_throw_reply_not_found_when_the_reply_belongs_to_another_tweet() {
            Reply ofAnotherTweet = TestDocuments.replyOn(UUID.randomUUID());
            callerIsKnown();
            when(replyRepository.updateContentIfAuthor(any(), any(), any(), anyString(), any())).thenReturn(false);

            assertThatThrownBy(() -> replyService.update(CALLER_ID, UUID.randomUUID(), ofAnotherTweet.getId(), "changed"))
                    .isInstanceOf(ReplyNotFoundException.class);
        }

        @Test
        void should_write_the_trimmed_text_and_the_clock_time_and_return_the_reread_reply_when_the_author_edits() {
            Reply reply = ownReply();
            callerIsKnown();
            when(replyRepository.updateContentIfAuthor(any(), any(), any(), anyString(), any())).thenReturn(true);
            when(replyRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

            ReplyResponseDTO response = replyService.update(CALLER_ID, reply.getTweetId(), reply.getId(), "  changed  ");

            verify(replyRepository).updateContentIfAuthor(
                    reply.getId(), reply.getTweetId(), CALLER_ID, "changed", Instant.parse("2026-03-04T05:06:07.123Z"));
            assertThat(response.getId()).isEqualTo(reply.getId());
            assertThat(response.getAuthor().getUsername()).isEqualTo("ana");
        }

        @Test
        void should_throw_empty_and_look_nothing_up_when_the_edited_text_is_blank() {
            assertThatThrownBy(() -> replyService.update(CALLER_ID, UUID.randomUUID(), UUID.randomUUID(), " "))
                    .isInstanceOf(EmptyReplyException.class);

            verifyNoInteractions(userLookupService, replyRepository);
        }

        private Reply ownReply() {
            Reply reply = TestDocuments.replyOn(UUID.randomUUID());
            reply.setAuthorId(CALLER_ID);

            return reply;
        }
    }

    @Nested
    class Delete {

        @Test
        void should_remove_the_reply_when_its_author_deletes() {
            Reply reply = replyBy(CALLER_ID);
            tweetBy(reply.getTweetId(), UUID.randomUUID());
            when(replyRepository.deleteByIdAndTweetId(reply.getId(), reply.getTweetId())).thenReturn(true);
            when(tweetRepository.incrementReplyCount(reply.getTweetId(), -1)).thenReturn(true);

            replyService.delete(CALLER_ID, reply.getTweetId(), reply.getId());

            verify(replyRepository).deleteByIdAndTweetId(reply.getId(), reply.getTweetId());
            verify(tweetRepository).incrementReplyCount(reply.getTweetId(), -1);
        }

        @Test
        void should_remove_the_reply_when_the_tweet_author_deletes() {
            Reply reply = replyBy(UUID.randomUUID());
            tweetBy(reply.getTweetId(), CALLER_ID);
            when(replyRepository.deleteByIdAndTweetId(reply.getId(), reply.getTweetId())).thenReturn(true);
            when(tweetRepository.incrementReplyCount(reply.getTweetId(), -1)).thenReturn(true);

            replyService.delete(CALLER_ID, reply.getTweetId(), reply.getId());

            verify(replyRepository).deleteByIdAndTweetId(reply.getId(), reply.getTweetId());
        }

        @Test
        void should_throw_reply_not_found_when_anyone_else_deletes() {
            Reply reply = replyBy(UUID.randomUUID());
            tweetBy(reply.getTweetId(), UUID.randomUUID());

            assertThatThrownBy(() -> replyService.delete(CALLER_ID, reply.getTweetId(), reply.getId()))
                    .isInstanceOf(ReplyNotFoundException.class);

            verify(replyRepository, never()).deleteByIdAndTweetId(any(), any());
            verify(tweetRepository, never()).incrementReplyCount(any(), anyLong());
        }

        @Test
        void should_decrement_the_count_only_when_the_reply_was_removed() {
            Reply reply = replyBy(CALLER_ID);
            tweetBy(reply.getTweetId(), UUID.randomUUID());
            when(replyRepository.deleteByIdAndTweetId(reply.getId(), reply.getTweetId())).thenReturn(false);

            assertThatThrownBy(() -> replyService.delete(CALLER_ID, reply.getTweetId(), reply.getId()))
                    .isInstanceOf(ReplyNotFoundException.class);

            verify(tweetRepository, never()).incrementReplyCount(any(), anyLong());
        }

        @Test
        void should_throw_reply_not_found_when_the_reply_belongs_to_another_tweet() {
            Reply reply = replyBy(CALLER_ID);

            assertThatThrownBy(() -> replyService.delete(CALLER_ID, UUID.randomUUID(), reply.getId()))
                    .isInstanceOf(ReplyNotFoundException.class);

            verify(replyRepository, never()).deleteByIdAndTweetId(any(), any());
        }

        @Test
        void should_throw_reply_not_found_when_the_reply_is_gone_after_a_write_conflict() {
            Reply reply = replyBy(CALLER_ID);
            tweetBy(reply.getTweetId(), UUID.randomUUID());
            when(replyRepository.deleteByIdAndTweetId(reply.getId(), reply.getTweetId())).thenThrow(new MongoException(112, "conflict"));
            when(replyRepository.existsById(reply.getId())).thenReturn(false);

            assertThatThrownBy(() -> replyService.delete(CALLER_ID, reply.getTweetId(), reply.getId()))
                    .isInstanceOf(ReplyNotFoundException.class);

            verify(replyRepository, times(1)).deleteByIdAndTweetId(any(), any());
        }

        @Test
        void should_retry_the_removal_when_a_write_conflict_leaves_the_reply_in_place() {
            Reply reply = replyBy(CALLER_ID);
            tweetBy(reply.getTweetId(), UUID.randomUUID());
            when(replyRepository.deleteByIdAndTweetId(reply.getId(), reply.getTweetId()))
                    .thenThrow(new MongoException(112, "conflict"))
                    .thenReturn(true);
            when(replyRepository.existsById(reply.getId())).thenReturn(true);
            when(tweetRepository.incrementReplyCount(reply.getTweetId(), -1)).thenReturn(true);

            replyService.delete(CALLER_ID, reply.getTweetId(), reply.getId());

            verify(replyRepository, times(2)).deleteByIdAndTweetId(reply.getId(), reply.getTweetId());
            verify(tweetRepository, times(1)).incrementReplyCount(reply.getTweetId(), -1);
        }

        private Reply replyBy(UUID authorId) {
            Reply reply = TestDocuments.replyOn(UUID.randomUUID());
            reply.setAuthorId(authorId);
            when(replyRepository.findById(reply.getId())).thenReturn(Optional.of(reply));

            return reply;
        }

        private void tweetBy(UUID tweetId, UUID authorId) {
            Tweet tweet = TestDocuments.tweetBy(authorId);
            tweet.setId(tweetId);
            when(tweetRepository.findById(tweetId)).thenReturn(Optional.of(tweet));
        }
    }

}