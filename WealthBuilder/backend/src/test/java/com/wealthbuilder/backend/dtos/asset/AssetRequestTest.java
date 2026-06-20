package com.wealthbuilder.backend.dtos.asset;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Bean-validation test for the asset request payload. Constraints are exercised directly via a
 * Jakarta {@link Validator}, with no Spring context, so each rule is verified in isolation.
 */
class AssetRequestTest {

    private static final String VALID_NAME = "Stocks";

    private static final String VALID_DESCRIPTION = "Equity instruments traded on public markets.";

    private static final String VALID_IMAGE = "data:image/png;base64,aGVsbG8=";

    private static final String VALID_IMAGE_NAME = "stocks.png";

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Nested
    @DisplayName("Valid payload")
    class Valid {

        @Test
        void should_HaveNoViolations_When_AllFieldsValid() {
            final Set<ConstraintViolation<AssetRequest>> violations =
                    validator.validate(request(VALID_NAME, VALID_DESCRIPTION, VALID_IMAGE));

            assertThat(violations).isEmpty();
        }

        @Test
        void should_HaveNoViolations_When_ImageIsJpeg() {
            final AssetRequest request = request(VALID_NAME, VALID_DESCRIPTION,
                    "data:image/jpeg;base64,aGVsbG8=");

            assertThat(validator.validate(request)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Name constraints")
    class Name {

        @Test
        void should_RejectBlankName() {
            assertThat(violatedFields(request("   ", VALID_DESCRIPTION, VALID_IMAGE)))
                    .contains("name");
        }

        @Test
        void should_RejectNameOverHundredCharacters() {
            final String tooLong = "n".repeat(101);

            assertThat(violatedFields(request(tooLong, VALID_DESCRIPTION, VALID_IMAGE)))
                    .contains("name");
        }

        @Test
        void should_AcceptNameAtHundredCharacterBoundary() {
            final String atLimit = "n".repeat(100);

            assertThat(violatedFields(request(atLimit, VALID_DESCRIPTION, VALID_IMAGE)))
                    .doesNotContain("name");
        }
    }

    @Nested
    @DisplayName("Description constraints")
    class Description {

        @Test
        void should_RejectBlankDescription() {
            assertThat(violatedFields(request(VALID_NAME, "", VALID_IMAGE)))
                    .contains("description");
        }

        @Test
        void should_RejectDescriptionOverThousandCharacters() {
            final String tooLong = "d".repeat(1001);

            assertThat(violatedFields(request(VALID_NAME, tooLong, VALID_IMAGE)))
                    .contains("description");
        }

        @Test
        void should_AcceptDescriptionAtThousandCharacterBoundary() {
            final String atLimit = "d".repeat(1000);

            assertThat(violatedFields(request(VALID_NAME, atLimit, VALID_IMAGE)))
                    .doesNotContain("description");
        }
    }

    @Nested
    @DisplayName("Image constraints")
    class Image {

        @Test
        void should_RejectBlankImage() {
            assertThat(violatedFields(request(VALID_NAME, VALID_DESCRIPTION, "")))
                    .contains("imageBase64");
        }

        @Test
        void should_RejectImageNotMatchingDataUriPattern() {
            assertThat(violatedFields(request(VALID_NAME, VALID_DESCRIPTION, "just-some-text")))
                    .contains("imageBase64");
        }

        @Test
        void should_RejectNonImageDataUri() {
            final AssetRequest request = request(VALID_NAME, VALID_DESCRIPTION,
                    "data:text/plain;base64,aGVsbG8=");

            assertThat(violatedFields(request)).contains("imageBase64");
        }
    }

    @Nested
    @DisplayName("Image name constraints")
    class ImageName {

        @Test
        void should_RejectBlankImageName() {
            assertThat(violatedFields(request(VALID_NAME, VALID_DESCRIPTION, VALID_IMAGE, "  ")))
                    .contains("imageName");
        }

        @Test
        void should_RejectImageNameOverTwoHundredFiftyFiveCharacters() {
            final String tooLong = "n".repeat(256);

            assertThat(violatedFields(request(VALID_NAME, VALID_DESCRIPTION, VALID_IMAGE, tooLong)))
                    .contains("imageName");
        }
    }

    private static Set<String> violatedFields(AssetRequest request) {
        return validator
                .validate(request)
                .stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static AssetRequest request(String name, String description, String imageBase64) {
        return request(name, description, imageBase64, VALID_IMAGE_NAME);
    }

    private static AssetRequest request(String name, String description, String imageBase64,
                                        String imageName) {
        final AssetRequest request = new AssetRequest();
        request.setName(name);
        request.setDescription(description);
        request.setImageBase64(imageBase64);
        request.setImageName(imageName);

        return request;
    }
}
