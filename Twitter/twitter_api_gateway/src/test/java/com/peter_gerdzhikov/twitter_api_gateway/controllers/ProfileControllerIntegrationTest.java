package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.UpdateProfileRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.profile.ProfileResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.files.DbFile;
import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUsers;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.profiles.ProfileMapper;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class ProfileControllerIntegrationTest extends AbstractMinioIntegrationTest {

    private static final Set<String> PROFILE_FIELDS = Set.of(
            "id",
            "bio",
            "username",
            "location",
            "createdAt",
            "birthdate",
            "followedByMe",
            "followersCount",
            "followingCount",
            "coverPictureUrl",
            "profilePictureUrl"
    );

    @Value("${app.upload.max-file-bytes}")
    private int maxFileBytes;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private DbFileRepository dbFileRepository;

    private User confirmedUser() {
        return saveUser(true);
    }

    private User saveUser(boolean enabled) {
        User user = TestEntities.newUser();
        user.setEnabled(enabled);

        return userRepository.save(user);
    }

    private User reload(User user) {
        return userRepository.findById(user.getId()).orElseThrow();
    }

    private String cookieOf(User user) {
        return tokenService.mint(user);
    }

    private JsonNode jsonOf(RestTestClient.ResponseSpec response) {
        String body = response
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        return objectMapper.readTree(body);
    }

    private Set<String> fieldNamesOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);

        return Set.copyOf(names);
    }

    @Nested
    class GetProfile {

        @Test
        void should_return_200_with_the_profile_shape_when_the_user_exists() {
            DbFile profilePicture = dbFileRepository.save(TestEntities.newDbFile());
            DbFile coverPicture = dbFileRepository.save(TestEntities.newDbFile());
            User target = confirmedUser();
            target.setBio("Hello there");
            target.setLocation("Sofia");
            target.setBirthdate(LocalDate.of(1990, 5, 17));
            target.setProfilePicture(profilePicture);
            target.setProfileCoverPicture(coverPicture);
            userRepository.save(target);

            JsonNode body = jsonOf(getProfile(target.getUsername(), cookieOf(confirmedUser())));

            assertThat(fieldNamesOf(body)).isEqualTo(PROFILE_FIELDS);
            assertThat(body.get("id").asString()).isEqualTo(target.getId().toString());
            assertThat(body.get("username").asString()).isEqualTo(target.getUsername());
            assertThat(body.get("bio").asString()).isEqualTo("Hello there");
            assertThat(body.get("location").asString()).isEqualTo("Sofia");
            assertThat(body.get("birthdate").asString()).isEqualTo("1990-05-17");
            assertThat(body.get("profilePictureUrl").asString()).isEqualTo("/api/v1/files/" + profilePicture.getId());
            assertThat(body.get("coverPictureUrl").asString()).isEqualTo("/api/v1/files/" + coverPicture.getId());
            assertThat(body.get("createdAt").asString()).isNotBlank();
        }

        @Test
        void should_return_null_picture_urls_when_the_user_has_no_pictures() {
            User target = confirmedUser();

            JsonNode body = jsonOf(getProfile(target.getUsername(), cookieOf(target)));

            assertThat(body.get("profilePictureUrl").isNull()).isTrue();
            assertThat(body.get("coverPictureUrl").isNull()).isTrue();
        }

        @Test
        void should_return_200_when_the_username_differs_only_in_case() {
            User target = confirmedUser();

            ProfileResponseDTO body = getProfile(target.getUsername().toUpperCase(), cookieOf(target))
                    .expectStatus()
                    .isOk()
                    .expectBody(ProfileResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isEqualTo(target.getId());
        }

        @Test
        void should_return_404_when_the_user_is_unknown() {
            assertNotFound(getProfile(TestUsers.unique().getUsername(), cookieOf(confirmedUser())));
        }

        @Test
        void should_return_404_when_the_user_is_unconfirmed() {
            User pending = saveUser(false);

            assertNotFound(getProfile(pending.getUsername(), cookieOf(confirmedUser())));
        }

        @Test
        void should_return_401_when_there_is_no_cookie() {
            User target = confirmedUser();

            getProfile(target.getUsername(), null)
                    .expectStatus()
                    .isUnauthorized();
        }

        private RestTestClient.ResponseSpec getProfile(String username, String cookie) {
            RestTestClient.RequestHeadersSpec<?> request = restTestClient
                    .get()
                    .uri("/api/v1/users/{username}", username);
            if (cookie != null) {
                request.cookie(CookieFactory.COOKIE_NAME, cookie);
            }

            return request.exchange();
        }

        private void assertNotFound(RestTestClient.ResponseSpec response) {
            ErrorResponseDTO body = response
                    .expectStatus()
                    .isNotFound()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).containsExactly("User not found.");
        }
    }

    @Nested
    class EditProfile {

        @Test
        void should_return_200_and_set_all_fields_when_the_request_is_valid() {
            User user = confirmedUser();
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("Hello there")
                    .location("Sofia")
                    .birthdate(LocalDate.of(1990, 5, 17))
                    .build();

            ProfileResponseDTO body = patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isOk()
                    .expectBody(ProfileResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isEqualTo(user.getId());
            assertThat(body.getBio()).isEqualTo("Hello there");
            assertThat(body.getLocation()).isEqualTo("Sofia");
            assertThat(body.getBirthdate()).isEqualTo(LocalDate.of(1990, 5, 17));
            User stored = reload(user);
            assertThat(stored.getBio()).isEqualTo("Hello there");
            assertThat(stored.getLocation()).isEqualTo("Sofia");
            assertThat(stored.getBirthdate()).isEqualTo(LocalDate.of(1990, 5, 17));
        }

        @Test
        void should_return_200_and_keep_the_fields_when_they_are_null() {
            User user = confirmedUserWithProfileText();

            patchProfile(new UpdateProfileRequestDTO(), cookieOf(user))
                    .expectStatus()
                    .isOk();

            assertProfileTextKept(reload(user));
        }

        @Test
        void should_keep_the_bio_and_the_birthdate_when_only_the_location_is_sent() {
            User user = confirmedUserWithProfileText();
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .location("Plovdiv")
                    .build();

            patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isOk();

            User stored = reload(user);
            assertThat(stored.getLocation()).isEqualTo("Plovdiv");
            assertThat(stored.getBio()).isEqualTo("old bio");
            assertThat(stored.getBirthdate()).isEqualTo(LocalDate.of(1980, 1, 1));
        }

        @Test
        void should_return_200_and_clear_the_text_fields_when_they_are_blank() {
            User user = confirmedUserWithProfileText();
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("   ")
                    .location("")
                    .build();

            patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isOk();

            User stored = reload(user);
            assertThat(stored.getBio()).isNull();
            assertThat(stored.getLocation()).isNull();
            assertThat(stored.getBirthdate()).isEqualTo(LocalDate.of(1980, 1, 1));
        }

        @Test
        void should_trim_the_strings_when_they_have_surrounding_whitespace() {
            User user = confirmedUser();
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("  Hello  ")
                    .location(" Sofia ")
                    .build();

            ProfileResponseDTO body = patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isOk()
                    .expectBody(ProfileResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getBio()).isEqualTo("Hello");
            assertThat(body.getLocation()).isEqualTo("Sofia");
            assertThat(reload(user).getBio()).isEqualTo("Hello");
        }

        @Test
        void should_return_200_and_store_the_bio_when_it_is_160_emoji() {
            User user = confirmedUser();
            String bio = "\uD83D\uDE00".repeat(160);
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio(bio)
                    .build();

            patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isOk();

            assertThat(reload(user).getBio()).isEqualTo(bio);
        }

        @Test
        void should_return_400_when_the_bio_is_161_emoji() {
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("\uD83D\uDE00".repeat(161))
                    .build();

            assertRejectedNaming(request, "bio");
        }

        @Test
        void should_return_400_when_the_bio_is_longer_than_160_characters() {
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("b".repeat(161))
                    .build();

            assertRejectedNaming(request, "bio");
        }

        @Test
        void should_return_200_and_store_the_location_when_it_is_60_characters() {
            User user = confirmedUser();
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .location("l".repeat(60))
                    .build();

            patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isOk();

            assertThat(reload(user).getLocation()).isEqualTo("l".repeat(60));
        }

        @Test
        void should_return_400_when_the_location_is_longer_than_60_characters() {
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .location("l".repeat(61))
                    .build();

            assertRejectedNaming(request, "location");
        }

        @Test
        void should_return_400_when_the_birthdate_is_in_the_future() {
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .birthdate(LocalDate.now().plusYears(1))
                    .build();

            assertRejectedNaming(request, "birthdate");
        }

        @Test
        void should_not_touch_the_pictures_when_the_profile_is_edited() {
            DbFile profilePicture = dbFileRepository.save(TestEntities.newDbFile());
            DbFile coverPicture = dbFileRepository.save(TestEntities.newDbFile());
            User user = confirmedUser();
            user.setProfilePicture(profilePicture);
            user.setProfileCoverPicture(coverPicture);
            userRepository.save(user);
            UpdateProfileRequestDTO request = UpdateProfileRequestDTO.builder()
                    .bio("Hello")
                    .build();

            ProfileResponseDTO body = patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isOk()
                    .expectBody(ProfileResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getProfilePictureUrl()).isEqualTo("/api/v1/files/" + profilePicture.getId());
            assertThat(body.getCoverPictureUrl()).isEqualTo("/api/v1/files/" + coverPicture.getId());
            User stored = reload(user);
            assertThat(stored.getProfilePicture().getId()).isEqualTo(profilePicture.getId());
            assertThat(stored.getProfileCoverPicture().getId()).isEqualTo(coverPicture.getId());
        }

        @Test
        void should_return_401_when_there_is_no_cookie() {
            patchProfile(new UpdateProfileRequestDTO(), null)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_405_when_the_profile_is_put() {
            User user = confirmedUser();

            restTestClient
                    .put()
                    .uri("/api/v1/users/me")
                    .cookie(CookieFactory.COOKIE_NAME, cookieOf(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new UpdateProfileRequestDTO())
                    .exchange()
                    .expectStatus()
                    .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        }

        private RestTestClient.ResponseSpec patchProfile(UpdateProfileRequestDTO request, String cookie) {
            RestTestClient.RequestBodySpec spec = restTestClient
                    .patch()
                    .uri("/api/v1/users/me");
            if (cookie != null) {
                spec.cookie(CookieFactory.COOKIE_NAME, cookie);
            }

            return spec
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .exchange();
        }

        private void assertRejectedNaming(UpdateProfileRequestDTO request, String field) {
            User user = confirmedUserWithProfileText();

            ErrorResponseDTO body = patchProfile(request, cookieOf(user))
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).anyMatch(message -> message.startsWith(field + " "));
            assertThat(reload(user).getBio()).isEqualTo("old bio");
        }

        private User confirmedUserWithProfileText() {
            User user = confirmedUser();
            user.setBio("old bio");
            user.setLocation("old location");
            user.setBirthdate(LocalDate.of(1980, 1, 1));

            return userRepository.save(user);
        }

        private void assertProfileTextKept(User stored) {
            assertThat(stored.getBio()).isEqualTo("old bio");
            assertThat(stored.getLocation()).isEqualTo("old location");
            assertThat(stored.getBirthdate()).isEqualTo(LocalDate.of(1980, 1, 1));
        }
    }

    @Nested
    class UploadPicture {

        @ParameterizedTest
        @CsvSource({
                "profile-picture, jpeg",
                "profile-picture, png",
                "profile-picture, webp",
                "cover-picture, jpeg",
                "cover-picture, png",
                "cover-picture, webp"
        })
        void should_return_200_and_store_the_object_and_row_when_the_image_is_valid(String slot, String format) throws Exception {
            User user = confirmedUser();
            byte[] image = imageBytes(format);

            MockHttpServletResponse response = putPicture(slot, "file", "photo." + format, image, cookieOf(user));

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            String url = urlOf(bodyOf(response), slot);
            assertThat(url).startsWith("/api/v1/files/");
            DbFile row = dbFileRepository.findById(fileIdOf(url)).orElseThrow();
            assertThat(row.getContentType()).isEqualTo("image/" + format);
            assertThat(row.getSizeBytes()).isEqualTo(image.length);
            assertThat(storedBytes(row.getObjectKey())).isEqualTo(image);
            assertThat(pointerIdOf(reload(user), slot)).isEqualTo(row.getId());
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_delete_the_old_object_and_row_and_return_a_new_url_when_a_picture_is_replaced(String slot) throws Exception {
            User user = confirmedUser();
            String cookie = cookieOf(user);
            String oldUrl = urlOf(uploadOk(slot, "png", cookie), slot);
            DbFile oldRow = dbFileRepository.findById(fileIdOf(oldUrl)).orElseThrow();

            String newUrl = urlOf(uploadOk(slot, "jpeg", cookie), slot);

            assertThat(newUrl).isNotEqualTo(oldUrl);
            assertThat(dbFileRepository.findById(oldRow.getId())).isEmpty();
            assertThat(objectExists(oldRow.getObjectKey())).isFalse();
            DbFile newRow = dbFileRepository.findById(fileIdOf(newUrl)).orElseThrow();
            assertThat(objectExists(newRow.getObjectKey())).isTrue();
            assertThat(pointerIdOf(reload(user), slot)).isEqualTo(newRow.getId());
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_415_when_the_image_is_a_gif(String slot) throws Exception {
            byte[] gif = "GIF89a\0\0\0\0\0\0\0\0".getBytes(StandardCharsets.ISO_8859_1);

            assertRejectedWith415(slot, "anim.gif", gif);
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_415_when_the_file_is_text_renamed_to_png(String slot) throws Exception {
            byte[] text = "this is not an image at all".getBytes(StandardCharsets.UTF_8);

            assertRejectedWith415(slot, "photo.png", text);
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_415_when_the_file_is_a_truncated_header(String slot) throws Exception {
            byte[] truncated = {(byte) 0x89, 0x50, 0x4E};

            assertRejectedWith415(slot, "photo.png", truncated);
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_413_when_the_file_is_over_5_mb(String slot) throws Exception {
            User user = confirmedUser();
            byte[] tooBig = Arrays.copyOf(imageBytes("png"), maxFileBytes + 1);

            MockHttpServletResponse response = putPicture(slot, "file", "big.png", tooBig, cookieOf(user));

            assertThat(response.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE.value());

            assertThat(pointerIdOf(reload(user), slot)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_400_when_the_file_is_empty(String slot) throws Exception {
            User user = confirmedUser();

            MockHttpServletResponse response = putPicture(slot, "file", "empty.png", new byte[0], cookieOf(user));

            assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

            assertThat(pointerIdOf(reload(user), slot)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_400_when_the_file_part_is_missing(String slot) throws Exception {
            User user = confirmedUser();

            MockHttpServletResponse response = putPicture(slot, "not-file", "photo.png", imageBytes("png"), cookieOf(user));

            assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());

            assertThat(pointerIdOf(reload(user), slot)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_401_when_there_is_no_cookie(String slot) throws Exception {
            MockHttpServletResponse response = putPicture(slot, "file", "photo.png", imageBytes("png"), null);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }

        private void assertRejectedWith415(String slot, String filename, byte[] content) throws Exception {
            User user = confirmedUser();

            MockHttpServletResponse response = putPicture(slot, "file", filename, content, cookieOf(user));

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
            ErrorResponseDTO body = objectMapper.readValue(
                    response.getContentAsString(StandardCharsets.UTF_8), ErrorResponseDTO.class);
            assertThat(body.getMessages()).containsExactly("Unsupported image type.");
            assertThat(pointerIdOf(reload(user), slot)).isNull();
        }
    }

    @Nested
    class DeletePicture {

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_204_and_remove_the_object_row_and_url_when_a_picture_exists(String slot) throws Exception {
            User user = confirmedUser();
            String cookie = cookieOf(user);
            String url = urlOf(uploadOk(slot, "png", cookie), slot);
            DbFile row = dbFileRepository.findById(fileIdOf(url)).orElseThrow();

            deletePicture(slot, cookie)
                    .expectStatus()
                    .isNoContent();

            assertThat(dbFileRepository.findById(row.getId())).isEmpty();
            assertThat(objectExists(row.getObjectKey())).isFalse();
            assertThat(pointerIdOf(reload(user), slot)).isNull();
            assertThat(urlOf(profileOf(user, cookie), slot)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_204_when_no_picture_exists(String slot) {
            User user = confirmedUser();

            deletePicture(slot, cookieOf(user))
                    .expectStatus()
                    .isNoContent();

            assertThat(pointerIdOf(reload(user), slot)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"profile-picture", "cover-picture"})
        void should_return_401_when_there_is_no_cookie(String slot) {
            deletePicture(slot, null)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_keep_the_other_slot_when_one_picture_is_deleted() throws Exception {
            User user = confirmedUser();
            String cookie = cookieOf(user);
            uploadOk("profile-picture", "png", cookie);
            uploadOk("cover-picture", "png", cookie);

            deletePicture("profile-picture", cookie)
                    .expectStatus()
                    .isNoContent();

            User stored = reload(user);
            assertThat(pointerIdOf(stored, "profile-picture")).isNull();
            assertThat(pointerIdOf(stored, "cover-picture")).isNotNull();
        }

        private ProfileResponseDTO profileOf(User user, String cookie) {
            return restTestClient
                    .get()
                    .uri("/api/v1/users/{username}", user.getUsername())
                    .cookie(CookieFactory.COOKIE_NAME, cookie)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody(ProfileResponseDTO.class)
                    .returnResult()
                    .getResponseBody();
        }
    }

    private MockHttpServletResponse putPicture(
            String slot, String partName, String filename, byte[] content, String cookie) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PUT, "/api/v1/users/me/" + slot)
                .file(new MockMultipartFile(partName, filename, null, content));
        if (cookie != null) {
            request.cookie(new Cookie(CookieFactory.COOKIE_NAME, cookie));
        }

        return mockMvc
                .perform(request)
                .andReturn()
                .getResponse();
    }

    private ProfileResponseDTO bodyOf(MockHttpServletResponse response) throws Exception {
        return objectMapper.readValue(
                response.getContentAsString(StandardCharsets.UTF_8), ProfileResponseDTO.class);
    }

    private RestTestClient.ResponseSpec deletePicture(String slot, String cookie) {
        RestTestClient.RequestHeadersSpec<?> spec = restTestClient
                .delete()
                .uri("/api/v1/users/me/" + slot);
        if (cookie != null) {
            spec.cookie(CookieFactory.COOKIE_NAME, cookie);
        }

        return spec.exchange();
    }

    private ProfileResponseDTO uploadOk(String slot, String format, String cookie) throws Exception {
        MockHttpServletResponse response = putPicture(slot, "file", "photo." + format, imageBytes(format), cookie);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());

        return bodyOf(response);
    }

    private byte[] imageBytes(String format) {
        byte[] signature = switch (format) {
            case "jpeg" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
            case "png" -> new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
            case "webp" -> "RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.ISO_8859_1);
            default -> throw new IllegalArgumentException(format);
        };
        byte[] image = Arrays.copyOf(signature, signature.length + 64);
        for (int i = signature.length; i < image.length; i++) {
            image[i] = (byte) i;
        }

        return image;
    }

    private String urlOf(ProfileResponseDTO profile, String slot) {
        return "profile-picture".equals(slot) ? profile.getProfilePictureUrl() : profile.getCoverPictureUrl();
    }

    private UUID fileIdOf(String url) {
        return UUID.fromString(url.substring(ProfileMapper.FILE_URL_PREFIX.length()));
    }

    private UUID pointerIdOf(User user, String slot) {
        DbFile pointer = "profile-picture".equals(slot) ? user.getProfilePicture() : user.getProfileCoverPicture();

        return pointer == null ? null : pointer.getId();
    }

    private byte[] storedBytes(String objectKey) throws Exception {
        try (InputStream stream = minioClient.getObject(GetObjectArgs.builder().bucket(TEST_BUCKET).object(objectKey).build())) {
            return stream.readAllBytes();
        }
    }

    private boolean objectExists(String objectKey) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(TEST_BUCKET).object(objectKey).build());
            return true;

        } catch (ErrorResponseException e) {
            return false;
        }
    }
}
