package com.wealthbuilder.backend.dtos.holding;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Bean-validation test for the holding request payload. Constraints are exercised directly via a
 * Jakarta {@link Validator}, with no Spring context, so each rule is verified in isolation. The
 * {@code @Digits} bounds mirror the entity's column precision.
 */
class HoldingRequestTest {

    private static final String VALID_NAME = "Apple shares";

    private static final BigDecimal VALID_AMOUNT = new BigDecimal("1500.0000");

    private static final BigDecimal VALID_QUANTITY = new BigDecimal("10.00000000");

    private static final LocalDate VALID_DATE = LocalDate.now();

    private static final String VALID_NOTE = "Bought on the dip.";

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
            final Set<ConstraintViolation<HoldingRequest>> violations =
                    validator.validate(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, VALID_DATE, VALID_NOTE));

            assertThat(violations).isEmpty();
        }

        @Test
        void should_HaveNoViolations_When_NoteIsNull() {
            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, VALID_DATE, null)))
                    .isEmpty();
        }

        @Test
        void should_HaveNoViolations_When_DateIsInThePast() {
            final LocalDate past = LocalDate.now().minusYears(3);

            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, past, VALID_NOTE)))
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("Name constraints")
    class Name {

        @Test
        void should_RejectBlankName() {
            assertThat(violatedFields(request("   ", VALID_AMOUNT, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .contains("name");
        }

        @Test
        void should_RejectNameOverTwoHundredCharacters() {
            final String tooLong = "n".repeat(201);

            assertThat(violatedFields(request(tooLong, VALID_AMOUNT, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .contains("name");
        }

        @Test
        void should_AcceptNameAtTwoHundredCharacterBoundary() {
            final String atLimit = "n".repeat(200);

            assertThat(violatedFields(request(atLimit, VALID_AMOUNT, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .doesNotContain("name");
        }
    }

    @Nested
    @DisplayName("Bought-for-amount constraints")
    class BoughtForAmount {

        @Test
        void should_RejectNullAmount() {
            assertThat(violatedFields(request(VALID_NAME, null, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .contains("boughtForAmount");
        }

        @Test
        void should_RejectZeroAmount() {
            assertThat(violatedFields(request(VALID_NAME, BigDecimal.ZERO, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .contains("boughtForAmount");
        }

        @Test
        void should_RejectNegativeAmount() {
            final BigDecimal negative = new BigDecimal("-1.0000");

            assertThat(violatedFields(request(VALID_NAME, negative, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .contains("boughtForAmount");
        }

        @Test
        void should_RejectAmountWithMoreThanFourFractionDigits() {
            final BigDecimal tooPrecise = new BigDecimal("1.00001");

            assertThat(violatedFields(request(VALID_NAME, tooPrecise, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .contains("boughtForAmount");
        }

        @Test
        void should_RejectAmountWithMoreThanFifteenIntegerDigits() {
            final BigDecimal tooLarge = new BigDecimal("1234567890123456.0000");

            assertThat(violatedFields(request(VALID_NAME, tooLarge, VALID_QUANTITY, VALID_DATE, VALID_NOTE)))
                    .contains("boughtForAmount");
        }
    }

    @Nested
    @DisplayName("Quantity constraints")
    class Quantity {

        @Test
        void should_RejectNullQuantity() {
            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, null, VALID_DATE, VALID_NOTE)))
                    .contains("quantity");
        }

        @Test
        void should_RejectZeroQuantity() {
            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, BigDecimal.ZERO, VALID_DATE, VALID_NOTE)))
                    .contains("quantity");
        }

        @Test
        void should_RejectNegativeQuantity() {
            final BigDecimal negative = new BigDecimal("-0.50000000");

            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, negative, VALID_DATE, VALID_NOTE)))
                    .contains("quantity");
        }

        @Test
        void should_RejectQuantityWithMoreThanEightFractionDigits() {
            final BigDecimal tooPrecise = new BigDecimal("1.000000001");

            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, tooPrecise, VALID_DATE, VALID_NOTE)))
                    .contains("quantity");
        }
    }

    @Nested
    @DisplayName("Date constraints")
    class Date {

        @Test
        void should_RejectNullDate() {
            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, null, VALID_NOTE)))
                    .contains("date");
        }

        @Test
        void should_RejectFutureDate() {
            final LocalDate future = LocalDate.now().plusDays(1);

            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, future, VALID_NOTE)))
                    .contains("date");
        }

        @Test
        void should_AcceptTodaysDate() {
            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, LocalDate.now(), VALID_NOTE)))
                    .doesNotContain("date");
        }
    }

    @Nested
    @DisplayName("Note constraints")
    class Note {

        @Test
        void should_RejectNoteOverThousandCharacters() {
            final String tooLong = "x".repeat(1001);

            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, VALID_DATE, tooLong)))
                    .contains("note");
        }

        @Test
        void should_AcceptNoteAtThousandCharacterBoundary() {
            final String atLimit = "x".repeat(1000);

            assertThat(violatedFields(request(VALID_NAME, VALID_AMOUNT, VALID_QUANTITY, VALID_DATE, atLimit)))
                    .doesNotContain("note");
        }
    }

    private static Set<String> violatedFields(HoldingRequest request) {
        return validator
                .validate(request)
                .stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static HoldingRequest request(
            String name,
            BigDecimal boughtForAmount,
            BigDecimal quantity,
            LocalDate date,
            String note) {
        final HoldingRequest request = new HoldingRequest();
        request.setName(name);
        request.setBoughtForAmount(boughtForAmount);
        request.setQuantity(quantity);
        request.setDate(date);
        request.setNote(note);

        return request;
    }
}
