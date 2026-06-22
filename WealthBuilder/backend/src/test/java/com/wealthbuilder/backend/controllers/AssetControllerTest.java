package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.asset.AssetRequest;
import com.wealthbuilder.backend.DTOs.asset.AssetResponse;
import com.wealthbuilder.backend.exceptions.asset.AssetInUseException;
import com.wealthbuilder.backend.exceptions.asset.AssetNameAlreadyTakenException;
import com.wealthbuilder.backend.exceptions.asset.AssetNotFoundException;
import com.wealthbuilder.backend.exceptions.GlobalExceptionHandler;
import com.wealthbuilder.backend.services.interfaces.AssetService;
import com.wealthbuilder.backend.config.AuthTokenCookie;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import com.wealthbuilder.backend.utils.DataUriImage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Web-slice test for the asset endpoints. A minimal security chain mirrors production intent —
 * every request must be authenticated and {@code @PreAuthorize} gates writes to moderators —
 * while {@code spring-security-test} post-processors stand in for the JWT filter. Identities
 * are injected directly so authorization, not token parsing, is what's under test here.
 */
@WebMvcTest(AssetController.class)
@Import({AssetControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class AssetControllerTest {

    private static final Long ASSET_ID = 7L;

    private static final String NAME = "Stocks";

    private static final String DESCRIPTION = "Equity instruments traded on public markets.";

    private static final String IMAGE = "data:image/png;base64,aGVsbG8=";

    private static final String IMAGE_NAME = "stocks.png";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssetService assetService;

    // The web slice eagerly instantiates the JwtAuthenticationFilter @Component, so its
    // collaborators must be present even though this test injects identities directly.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private AuthTokenCookie authTokenCookie;

    @Nested
    @DisplayName("Authentication required")
    class AuthenticationRequired {

        @Test
        void should_Return401_When_ListRequestedAnonymously() throws Exception {
            mockMvc
                    .perform(get("/api/assets"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void should_Return401_When_CreateRequestedAnonymously() throws Exception {
            mockMvc
                    .perform(post("/api/assets")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, IMAGE)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Reads (any authenticated user)")
    class Reads {

        @Test
        void should_Return200WithAssets_When_ListRequestedByUser() throws Exception {
            given(assetService.findAll(anyString(), anyBoolean())).willReturn(List.of(response()));

            mockMvc
                    .perform(get("/api/assets").with(user("alice").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(ASSET_ID))
                    .andExpect(jsonPath("$[0].name").value(NAME))
                    .andExpect(jsonPath("$[0].description").value(DESCRIPTION))
                    .andExpect(jsonPath("$[0].inUse").value(false));
        }

        @Test
        void should_Return200WithAsset_When_DetailRequestedByUser() throws Exception {
            given(assetService.findById(eq(ASSET_ID), anyString(), anyBoolean())).willReturn(response());

            mockMvc
                    .perform(get("/api/assets/{id}", ASSET_ID).with(user("alice").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(NAME));
        }

        @Test
        void should_Return404Problem_When_AssetMissing() throws Exception {
            given(assetService.findById(eq(ASSET_ID), anyString(), anyBoolean()))
                    .willThrow(new AssetNotFoundException(ASSET_ID));

            mockMvc
                    .perform(get("/api/assets/{id}", ASSET_ID).with(user("alice").roles("USER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Asset not found: id=" + ASSET_ID));
        }
    }

    @Nested
    @DisplayName("Image endpoint")
    class ImageEndpoint {

        @Test
        void should_ReturnRawBytesWithContentType_When_ImageRequested() throws Exception {
            final byte[] bytes = "hello-image".getBytes(StandardCharsets.UTF_8);
            given(assetService.findImage(ASSET_ID))
                    .willReturn(new DataUriImage(MediaType.IMAGE_PNG, bytes));

            mockMvc
                    .perform(get("/api/assets/{id}/image", ASSET_ID).with(user("alice").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", MediaType.IMAGE_PNG_VALUE))
                    .andExpect(header().string("Cache-Control", "max-age=3600, public"))
                    .andExpect(header().longValue("Content-Length", bytes.length))
                    .andExpect(content().bytes(bytes));
        }

        @Test
        void should_Return404Problem_When_ImageAssetMissing() throws Exception {
            given(assetService.findImage(ASSET_ID))
                    .willThrow(new AssetNotFoundException(ASSET_ID));

            mockMvc
                    .perform(get("/api/assets/{id}/image", ASSET_ID).with(user("alice").roles("USER")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Writes (moderator only)")
    class Writes {

        @Test
        void should_Return403_When_UserAttemptsCreate() throws Exception {
            mockMvc
                    .perform(post("/api/assets")
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, IMAGE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void should_Return201_When_ModeratorCreatesValidAsset() throws Exception {
            given(assetService.create(any(AssetRequest.class))).willReturn(response());

            mockMvc
                    .perform(post("/api/assets")
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, IMAGE)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value(NAME));

            verify(assetService).create(any(AssetRequest.class));
        }

        @Test
        void should_Return400WithErrors_When_ModeratorPostsBlankName() throws Exception {
            mockMvc
                    .perform(post("/api/assets")
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("", DESCRIPTION, IMAGE)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists());
        }

        @Test
        void should_Return400WithErrors_When_ModeratorPostsInvalidImage() throws Exception {
            mockMvc
                    .perform(post("/api/assets")
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, "not-a-data-uri")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.imageBase64").exists());
        }

        @Test
        void should_Return409Problem_When_ModeratorCreatesDuplicateName() throws Exception {
            given(assetService.create(any(AssetRequest.class)))
                    .willThrow(new AssetNameAlreadyTakenException(NAME));

            mockMvc
                    .perform(post("/api/assets")
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, IMAGE)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("Asset name already taken: " + NAME));
        }

        @Test
        void should_Return200_When_ModeratorUpdatesAsset() throws Exception {
            given(assetService.update(eq(ASSET_ID), any(AssetRequest.class))).willReturn(response());

            mockMvc
                    .perform(put("/api/assets/{id}", ASSET_ID)
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, IMAGE)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value(NAME));
        }

        @Test
        void should_Return403_When_UserAttemptsUpdate() throws Exception {
            mockMvc
                    .perform(put("/api/assets/{id}", ASSET_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, IMAGE)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void should_Return404Problem_When_ModeratorUpdatesMissingAsset() throws Exception {
            willThrow(new AssetNotFoundException(ASSET_ID))
                    .given(assetService).update(eq(ASSET_ID), any(AssetRequest.class));

            mockMvc
                    .perform(put("/api/assets/{id}", ASSET_ID)
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(NAME, DESCRIPTION, IMAGE)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void should_Return204_When_ModeratorDeletesAsset() throws Exception {
            mockMvc
                    .perform(delete("/api/assets/{id}", ASSET_ID)
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(assetService).delete(ASSET_ID);
        }

        @Test
        void should_Return409Problem_When_ModeratorDeletesAssetInUse() throws Exception {
            willThrow(new AssetInUseException(ASSET_ID))
                    .given(assetService).delete(ASSET_ID);

            mockMvc
                    .perform(delete("/api/assets/{id}", ASSET_ID)
                            .with(user("mod").roles("MODERATOR"))
                            .with(csrf()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail")
                            .value("Asset is referenced by existing holdings and cannot be deleted: " + ASSET_ID));
        }

        @Test
        void should_Return403_When_UserAttemptsDelete() throws Exception {
            mockMvc
                    .perform(delete("/api/assets/{id}", ASSET_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    private static AssetResponse response() {
        return new AssetResponse(ASSET_ID, null, NAME, DESCRIPTION, IMAGE_NAME, false);
    }

    private static String json(String name, String description, String imageBase64) {
        return "{\"name\":\"" + name + "\",\"description\":\"" + description
                + "\",\"imageBase64\":\"" + imageBase64
                + "\",\"imageName\":\"" + IMAGE_NAME + "\"}";
    }

    /**
     * Minimal stand-in for the production security chain: authentication is required for every
     * request and {@code @EnableMethodSecurity} activates the controller's {@code @PreAuthorize}
     * guards. The JWT filter is intentionally absent — identities arrive via test post-processors.
     */
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

            return http.build();
        }
    }
}
