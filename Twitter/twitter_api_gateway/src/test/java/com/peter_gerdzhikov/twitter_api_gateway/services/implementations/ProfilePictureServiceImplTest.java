package com.peter_gerdzhikov.twitter_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

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

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.PictureSlot;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.EmptyUploadException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.StorageUnavailableException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.files.UnsupportedImageTypeException;
import com.peter_gerdzhikov.twitter_api_gateway.exceptions.users.UserNotFoundException;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ObjectStorageService;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;

@ExtendWith(MockitoExtension.class)
class ProfilePictureServiceImplTest {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4
    };

    private static final long MAX_FILE_BYTES = 64;

    private static final UUID USER_ID = UUID.randomUUID();

    private ProfilePictureServiceImpl profilePictureService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DbFileRepository dbFileRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        profilePictureService = new ProfilePictureServiceImpl(
                MAX_FILE_BYTES, userRepository, dbFileRepository, transactionTemplate, objectStorageService);
    }

    @Nested
    class Upload {

        @Test
        void should_store_the_object_then_save_the_row_then_point_the_user_at_it() {
            User user = userWithoutPictures();
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));

            ProfileResponseDTO response = profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png());

            InOrder order = inOrder(objectStorageService, dbFileRepository);
            order.verify(objectStorageService).put(anyString(), any(), eq((long) PNG_BYTES.length), eq("image/png"));
            order.verify(dbFileRepository).save(any(DbFile.class));
            assertThat(user.getProfilePicture()).isNotNull();
            assertThat(response.getProfilePictureUrl()).isNotNull();
        }

        @Test
        void should_save_the_row_with_the_object_key_the_detected_type_and_the_size() {
            userWithoutPictures();
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));

            profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png());

            ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
            verify(objectStorageService).put(key.capture(), any(), anyLong(), anyString());
            ArgumentCaptor<DbFile> row = ArgumentCaptor.forClass(DbFile.class);
            verify(dbFileRepository).save(row.capture());
            assertThat(row.getValue().getObjectKey()).isEqualTo(key.getValue());
            assertThat(UUID.fromString(key.getValue())).isNotNull();
            assertThat(row.getValue().getContentType()).isEqualTo("image/png");
            assertThat(row.getValue().getSizeBytes()).isEqualTo(PNG_BYTES.length);
        }

        @Test
        void should_use_the_cover_slot_when_the_slot_is_cover_picture() {
            User user = userWithoutPictures();
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));

            profilePictureService.upload(USER_ID, PictureSlot.COVER_PICTURE, png());

            assertThat(user.getProfileCoverPicture()).isNotNull();
            assertThat(user.getProfilePicture()).isNull();
        }

        @Test
        void should_delete_the_old_row_and_then_the_old_object_when_a_picture_is_replaced() {
            DbFile old = TestEntities.newDbFile();
            User user = userWithoutPictures();
            user.setProfilePicture(old);
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));

            profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png());

            InOrder order = inOrder(dbFileRepository, objectStorageService);
            order.verify(dbFileRepository).delete(old);
            order.verify(objectStorageService).delete(old.getObjectKey());
            assertThat(user.getProfilePicture()).isNotSameAs(old);
        }

        @Test
        void should_not_delete_anything_when_there_was_no_previous_picture() {
            userWithoutPictures();
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));

            profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png());

            verify(dbFileRepository, never()).delete(any(DbFile.class));
            verify(objectStorageService, never()).delete(anyString());
        }

        @Test
        void should_delete_the_new_object_and_leave_the_pointer_when_saving_the_row_fails() {
            DbFile old = TestEntities.newDbFile();
            User user = userWithoutPictures();
            user.setProfilePicture(old);
            when(dbFileRepository.save(any(DbFile.class))).thenThrow(new IllegalStateException("db down"));

            assertThatThrownBy(() -> profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png()))
                    .isInstanceOf(IllegalStateException.class);

            ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
            verify(objectStorageService).put(newKey.capture(), any(), anyLong(), anyString());
            verify(objectStorageService).delete(newKey.getValue());
            verify(objectStorageService, never()).delete(old.getObjectKey());
            assertThat(user.getProfilePicture()).isSameAs(old);
        }

        @Test
        void should_lock_the_user_before_loading_it() {
            userWithoutPictures();
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));

            profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png());

            InOrder order = inOrder(userRepository);
            order.verify(userRepository).lockById(USER_ID);
            order.verify(userRepository).findById(USER_ID);
        }

        @Test
        void should_delete_the_new_object_when_the_user_no_longer_exists() {
            when(userRepository.lockById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png()))
                    .isInstanceOf(UserNotFoundException.class);

            ArgumentCaptor<String> newKey = ArgumentCaptor.forClass(String.class);
            verify(objectStorageService).put(newKey.capture(), any(), anyLong(), anyString());
            verify(objectStorageService).delete(newKey.getValue());
        }

        @Test
        void should_still_return_the_profile_when_deleting_the_old_object_fails() {
            DbFile old = TestEntities.newDbFile();
            User user = userWithoutPictures();
            user.setProfilePicture(old);
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));
            doThrow(new StorageUnavailableException(new RuntimeException("down")))
                    .when(objectStorageService)
                    .delete(old.getObjectKey());

            ProfileResponseDTO response = profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png());

            assertThat(response.getProfilePictureUrl()).isNotNull();
            assertThat(user.getProfilePicture()).isNotSameAs(old);
        }

        @Test
        void should_change_nothing_in_the_database_when_storing_the_object_fails() {
            doThrow(new StorageUnavailableException(new RuntimeException("down")))
                    .when(objectStorageService)
                    .put(anyString(), any(), anyLong(), anyString());

            assertThatThrownBy(() -> profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, png()))
                    .isInstanceOf(StorageUnavailableException.class);

            verifyNoInteractions(userRepository, dbFileRepository);
            verify(objectStorageService, never()).delete(anyString());
        }

        @Test
        void should_touch_neither_storage_nor_database_when_the_file_is_empty() {
            MockMultipartFile empty = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);

            assertThatThrownBy(() -> profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, empty))
                    .isInstanceOf(EmptyUploadException.class);

            verifyNoInteractions(objectStorageService, userRepository, dbFileRepository);
        }

        @Test
        void should_touch_neither_storage_nor_database_when_the_file_is_over_the_size_limit() {
            byte[] oversized = Arrays.copyOf(PNG_BYTES, (int) MAX_FILE_BYTES + 1);
            MockMultipartFile big = new MockMultipartFile("file", "a.png", "image/png", oversized);

            assertThatThrownBy(() -> profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, big))
                    .isInstanceOf(MaxUploadSizeExceededException.class);

            verifyNoInteractions(objectStorageService, userRepository, dbFileRepository);
        }

        @Test
        void should_accept_a_file_of_exactly_the_size_limit() {
            userWithoutPictures();
            when(dbFileRepository.save(any(DbFile.class))).thenAnswer(call -> call.getArgument(0));
            byte[] atLimit = Arrays.copyOf(PNG_BYTES, (int) MAX_FILE_BYTES);
            MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", atLimit);

            ProfileResponseDTO response = profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, file);

            assertThat(response.getProfilePictureUrl()).isNotNull();
        }

        @Test
        void should_touch_neither_storage_nor_database_when_the_bytes_are_not_an_image() {
            MockMultipartFile text = new MockMultipartFile("file", "a.png", "image/png", "just text bytes".getBytes());

            assertThatThrownBy(() -> profilePictureService.upload(USER_ID, PictureSlot.PROFILE_PICTURE, text))
                    .isInstanceOf(UnsupportedImageTypeException.class);

            verifyNoInteractions(objectStorageService, userRepository, dbFileRepository);
        }
    }

    @Nested
    class Delete {

        @Test
        void should_delete_the_row_clear_the_pointer_and_then_delete_the_object() {
            DbFile picture = TestEntities.newDbFile();
            User user = userWithoutPictures();
            user.setProfilePicture(picture);

            profilePictureService.delete(USER_ID, PictureSlot.PROFILE_PICTURE);

            InOrder order = inOrder(dbFileRepository, objectStorageService);
            order.verify(dbFileRepository).delete(picture);
            order.verify(objectStorageService).delete(picture.getObjectKey());
            assertThat(user.getProfilePicture()).isNull();
        }

        @Test
        void should_leave_the_other_slot_alone() {
            DbFile cover = TestEntities.newDbFile();
            User user = userWithoutPictures();
            user.setProfileCoverPicture(cover);
            user.setProfilePicture(TestEntities.newDbFile());

            profilePictureService.delete(USER_ID, PictureSlot.PROFILE_PICTURE);

            assertThat(user.getProfileCoverPicture()).isSameAs(cover);
        }

        @Test
        void should_do_nothing_when_the_slot_is_empty() {
            userWithoutPictures();

            profilePictureService.delete(USER_ID, PictureSlot.COVER_PICTURE);

            verifyNoInteractions(dbFileRepository, objectStorageService);
        }

        @Test
        void should_finish_normally_when_deleting_the_object_fails() {
            DbFile picture = TestEntities.newDbFile();
            userWithoutPictures().setProfilePicture(picture);
            doThrow(new StorageUnavailableException(new RuntimeException("down")))
                    .when(objectStorageService)
                    .delete(picture.getObjectKey());

            profilePictureService.delete(USER_ID, PictureSlot.PROFILE_PICTURE);

            verify(dbFileRepository).delete(picture);
        }

        @Test
        void should_keep_the_object_when_deleting_the_row_fails() {
            DbFile picture = TestEntities.newDbFile();
            userWithoutPictures().setProfilePicture(picture);
            doThrow(new IllegalStateException("db down")).when(dbFileRepository).delete(picture);

            assertThatThrownBy(() -> profilePictureService.delete(USER_ID, PictureSlot.PROFILE_PICTURE))
                    .isInstanceOf(IllegalStateException.class);

            verify(objectStorageService, never()).delete(anyString());
        }

        @Test
        void should_throw_when_the_user_no_longer_exists() {
            when(userRepository.lockById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> profilePictureService.delete(USER_ID, PictureSlot.PROFILE_PICTURE))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }

    private User userWithoutPictures() {
        User user = TestEntities.newUser();
        when(userRepository.lockById(USER_ID)).thenReturn(Optional.of(USER_ID));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        return user;
    }

    private MockMultipartFile png() {
        return new MockMultipartFile("file", "avatar.png", "image/png", PNG_BYTES);
    }
}
