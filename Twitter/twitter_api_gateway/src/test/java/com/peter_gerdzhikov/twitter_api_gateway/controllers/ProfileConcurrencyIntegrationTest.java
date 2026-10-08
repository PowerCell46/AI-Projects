package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.DbFileRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.auth.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.utilities.web.CookieFactory;

import jakarta.servlet.http.Cookie;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class ProfileConcurrencyIntegrationTest extends AbstractMinioIntegrationTest {

    private static final int USERS = 8;

    private static final long TIMEOUT_SECONDS = 60;

    private static final byte[] PNG_BYTES = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DbFileRepository dbFileRepository;

    @Test
    void should_keep_both_changes_when_a_profile_edit_and_a_picture_upload_run_in_parallel() throws Exception {
        List<User> users = new ArrayList<>();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            User user = confirmedUser();
            String cookie = tokenService.mint(user);
            users.add(user);
            calls.add(() -> editProfile(cookie));
            calls.add(() -> uploadPicture(cookie));
        }

        List<Integer> statuses = runInParallel(calls);

        assertThat(statuses).containsOnly(200);
        for (User user : users) {
            User stored = userRepository.findById(user.getId()).orElseThrow();
            assertThat(stored.getBio()).isEqualTo("edited bio");
            assertThat(stored.getLocation()).isEqualTo("Sofia");
            assertThat(stored.getProfilePicture()).isNotNull();
        }
    }

    @Test
    void should_leave_no_orphan_rows_when_uploads_to_one_slot_run_in_parallel() throws Exception {
        User user = confirmedUser();
        String cookie = tokenService.mint(user);
        long rowsBefore = dbFileRepository.count();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < USERS; i++) {
            calls.add(() -> uploadPicture(cookie));
        }

        List<Integer> statuses = runInParallel(calls);

        assertThat(statuses).containsOnly(200);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getProfilePicture()).isNotNull();
        assertThat(dbFileRepository.count() - rowsBefore).isEqualTo(1);
    }

    /**
     * Releases every call at once through a latch, so each user's edit and upload overlap instead of
     * running in submission order.
     */
    private List<Integer> runInParallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }

            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            return statuses;

        } finally {
            executor.shutdownNow();
        }
    }

    private int editProfile(String cookie) throws Exception {
        return mockMvc
                .perform(patch("/api/v1/users/me")
                        .cookie(new Cookie(CookieFactory.COOKIE_NAME, cookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\":\"edited bio\",\"location\":\"Sofia\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int uploadPicture(String cookie) throws Exception {
        return mockMvc
                .perform(multipart(HttpMethod.PUT, "/api/v1/users/me/profile-picture")
                        .file(new MockMultipartFile("file", "photo.png", null, PNG_BYTES))
                        .cookie(new Cookie(CookieFactory.COOKIE_NAME, cookie)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private User confirmedUser() {
        User user = TestEntities.newUser();
        user.setEnabled(true);

        return userRepository.save(user);
    }
}
