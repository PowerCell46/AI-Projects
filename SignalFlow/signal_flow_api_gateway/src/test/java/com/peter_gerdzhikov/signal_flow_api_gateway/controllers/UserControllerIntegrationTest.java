package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.auth.UserResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class UserControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String COOKIE_NAME = "access_token";
    private static final String PASSWORD = "Password123";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private TokenService tokenService;

    private String adminEmail;

    private String adminCookie;

    @BeforeEach
    void clearAndCreateAdmin() {
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
        adminEmail = uniqueEmail("admin");
        adminCookie = tokenService.mint(saveUser(adminEmail, Role.ADMIN));
    }

    @Test
    void should_delete_a_user_with_their_subscriptions_and_leave_others_untouched() {
        User target = saveUser(uniqueEmail("target"), Role.USER);
        User bystander = saveUser(uniqueEmail("bystander"), Role.USER);
        subscribe(target, UUID.randomUUID());
        subscribe(target, UUID.randomUUID());
        subscribe(bystander, UUID.randomUUID());

        deleteUser(adminCookie, target.getEmail()).expectStatus().isNoContent();

        assertThat(userRepository.existsByEmail(target.getEmail())).isFalse();
        assertThat(subscriptionRepository.countByUser_Id(target.getId())).isZero();
        assertThat(userRepository.existsByEmail(bystander.getEmail())).isTrue();
        assertThat(subscriptionRepository.countByUser_Id(bystander.getId())).isEqualTo(1);
    }

    @Test
    void should_find_the_account_when_the_email_is_given_in_mixed_case() {
        User target = saveUser(uniqueEmail("target"), Role.USER);

        deleteUser(adminCookie, target.getEmail().toUpperCase()).expectStatus().isNoContent();

        assertThat(userRepository.existsByEmail(target.getEmail())).isFalse();
    }

    @Test
    void should_let_the_person_register_again_as_a_fresh_account_after_deletion() {
        String email = uniqueEmail("returning");
        UUID oldId = register(email).getId();
        subscribe(userRepository.getReferenceById(oldId), UUID.randomUUID());

        deleteUser(adminCookie, email).expectStatus().isNoContent();

        login(email).expectStatus().isUnauthorized();
        UUID newId = register(email).getId();
        assertThat(newId).isNotEqualTo(oldId);
        assertThat(subscriptionRepository.countByUser_Id(newId)).isZero();
    }

    @Test
    void should_return_403_and_keep_the_account_when_the_target_is_an_admin() {
        User otherAdmin = saveUser(uniqueEmail("other-admin"), Role.ADMIN);

        deleteUser(adminCookie, otherAdmin.getEmail()).expectStatus().isForbidden();

        assertThat(userRepository.existsByEmail(otherAdmin.getEmail())).isTrue();
    }

    @Test
    void should_return_403_and_keep_the_account_when_the_admin_targets_themselves() {
        deleteUser(adminCookie, adminEmail).expectStatus().isForbidden();

        assertThat(userRepository.existsByEmail(adminEmail)).isTrue();
    }

    @Test
    void should_return_404_when_no_account_has_the_email() {
        long usersBefore = userRepository.count();

        deleteUser(adminCookie, uniqueEmail("nobody")).expectStatus().isNotFound();

        assertThat(userRepository.count()).isEqualTo(usersBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "not-an-email"})
    void should_return_400_and_delete_nothing_for_a_blank_or_malformed_email(String email) {
        User target = saveUser(uniqueEmail("target"), Role.USER);
        long usersBefore = userRepository.count();

        deleteUser(adminCookie, email).expectStatus().isBadRequest();

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(userRepository.existsByEmail(target.getEmail())).isTrue();
    }

    @Test
    void should_return_400_when_the_email_is_missing() {
        User target = saveUser(uniqueEmail("target"), Role.USER);

        restTestClient.delete()
                .uri("/api/v1/users")
                .cookie(COOKIE_NAME, adminCookie)
                .exchange()
                .expectStatus().isBadRequest();

        assertThat(userRepository.existsByEmail(target.getEmail())).isTrue();
    }

    @Test
    void should_return_403_and_delete_nothing_when_the_caller_is_not_an_admin() {
        User caller = saveUser(uniqueEmail("caller"), Role.USER);
        User target = saveUser(uniqueEmail("target"), Role.USER);

        deleteUser(tokenService.mint(caller), target.getEmail()).expectStatus().isForbidden();

        assertThat(userRepository.existsByEmail(target.getEmail())).isTrue();
    }

    @Test
    void should_return_401_with_no_cookie() {
        User target = saveUser(uniqueEmail("target"), Role.USER);

        restTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users")
                        .queryParam("email", target.getEmail())
                        .build())
                .exchange()
                .expectStatus().isUnauthorized();

        assertThat(userRepository.existsByEmail(target.getEmail())).isTrue();
    }

    private RestTestClient.ResponseSpec deleteUser(String cookie, String email) {
        return restTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/users")
                        .queryParam("email", email)
                        .build())
                .cookie(COOKIE_NAME, cookie)
                .exchange();
    }

    private UserResponseDTO register(String email) {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail(email);
        request.setPassword(PASSWORD);

        return restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponseDTO.class)
                .returnResult()
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec login(String email) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(email);
        request.setPassword(PASSWORD);

        return restTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("hashed-password");
        user.setRole(role);
        return userRepository.save(user);
    }

    private void subscribe(User user, UUID interestTopicId) {
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setInterestTopicId(interestTopicId);
        subscriptionRepository.save(subscription);
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
