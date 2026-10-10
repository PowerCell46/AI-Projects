package com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery;

import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.peter_gerdzhikov.twitter_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.TransientMailDeliveryException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailDeliveryServiceImplTest {

    private static final String FROM_ADDRESS = "twitter@example.com";

    private static final String RECIPIENT = "recipient@example.com";

    private static final String HTML = "<p>Hello</p>";

    private static final String TEXT = "Hello";

    @Mock
    private JavaMailSender mailSender;

    private MimeMessage message;

    private MailDeliveryServiceImpl mailDeliveryService;

    @BeforeEach
    void setUp() {
        message = new MimeMessage(Session.getInstance(new Properties()));
        mailDeliveryService = new MailDeliveryServiceImpl(FROM_ADDRESS, mailSender);
    }

    @Nested
    class Constructor {

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "twitter", "twitter@", "@example.com", "twi tter@example.com", "twitter@@example.com"})
        void should_fail_to_start_when_the_sender_is_not_an_email_address(String fromAddress) {
            assertThrows(IllegalStateException.class, () -> new MailDeliveryServiceImpl(fromAddress, mailSender));
        }

        @Test
        void should_not_echo_the_sender_in_the_failure() {
            IllegalStateException thrown = assertThrows(IllegalStateException.class,
                    () -> new MailDeliveryServiceImpl("typo-sender", mailSender));

            assertTrue(thrown.getMessage().contains("MAIL_FROM"));
            assertFalse(thrown.getMessage().contains("typo-sender"));
            assertNull(thrown.getCause());
        }

        @ParameterizedTest
        @ValueSource(strings = {"twitter@example.com", "Twitter <twitter@example.com>", "twitter+noreply@mail.example.co.uk"})
        void should_start_when_the_sender_is_a_valid_address(String fromAddress) {
            assertDoesNotThrow(() -> new MailDeliveryServiceImpl(fromAddress, mailSender));
        }
    }

    @Nested
    class Send {

        @Test
        void should_set_recipient_sender_and_subject_and_send_the_message_once() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);

            mailDeliveryService.send(RECIPIENT, "Confirm your email", HTML, TEXT);
            message.saveChanges(); // the real JavaMailSenderImpl.send() does this before transmitting

            assertTrue(message.getAllRecipients()[0].toString().contains(RECIPIENT));
            assertTrue(message.getFrom()[0].toString().contains(FROM_ADDRESS));
            assertEquals("Confirm your email", message.getSubject());
            verify(mailSender).send(message);
        }

        @Test
        void should_carry_both_a_text_plain_and_a_text_html_part() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);

            mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT);
            message.saveChanges();

            assertEquals(HTML, findPart(message, "text/html").getContent());
            assertEquals(TEXT, findPart(message, "text/plain").getContent());
        }

        @Test
        void should_strip_line_breaks_from_the_subject() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);

            mailDeliveryService.send(RECIPIENT, "Confirm\r\nBcc: victim@example.com", HTML, TEXT);
            message.saveChanges();

            assertEquals("ConfirmBcc: victim@example.com", message.getSubject());
            assertNull(message.getHeader("Bcc"));
        }

        @Test
        void should_round_trip_a_cyrillic_subject_and_body_through_utf8() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);

            mailDeliveryService.send(RECIPIENT, "Потвърдете имейла си", "<p>Здравей</p>", "Здравей");
            message.saveChanges();

            assertEquals("Потвърдете имейла си", message.getSubject());
            assertEquals("<p>Здравей</p>", findPart(message, "text/html").getContent());
        }
    }

    @Nested
    class Classify {

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery.MailDeliveryServiceImplTest#smtpReturnCodes")
        void should_classify_an_smtp_address_failure_by_its_return_code(int returnCode, boolean expectedPermanent) throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);
            SMTPAddressFailedException smtpException = new SMTPAddressFailedException(
                    new InternetAddress(RECIPIENT), "RCPT TO", returnCode, "smtp reply");
            doThrow(new MailSendException("send failed", smtpException)).when(mailSender).send(any(MimeMessage.class));

            Class<? extends RuntimeException> expectedType = expectedPermanent
                    ? PermanentMailDeliveryException.class
                    : TransientMailDeliveryException.class;

            assertThrows(expectedType, () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));
        }

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery.MailDeliveryServiceImplTest#smtpReturnCodes")
        void should_classify_an_smtp_send_failure_by_its_return_code(int returnCode, boolean expectedPermanent) {
            when(mailSender.createMimeMessage()).thenReturn(message);
            SMTPSendFailedException smtpException = new SMTPSendFailedException(
                    "DATA", returnCode, "smtp reply", null, null, null, null);
            doThrow(new MailSendException("send failed", smtpException)).when(mailSender).send(any(MimeMessage.class));

            Class<? extends RuntimeException> expectedType = expectedPermanent
                    ? PermanentMailDeliveryException.class
                    : TransientMailDeliveryException.class;

            assertThrows(expectedType, () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));
        }

        @Test
        void should_classify_permanent_when_the_real_javamailsenderimpl_shape_has_no_direct_cause() throws Exception {
            // The real JavaMailSenderImpl.doSend() builds MailSendException via its per-message Map
            // constructor, which leaves getCause() null - the real failure is only reachable through
            // getMessageExceptions(). The (message, cause) constructor used in the other tests here does not
            // reproduce this; this test guards that gap.
            when(mailSender.createMimeMessage()).thenReturn(message);
            SMTPAddressFailedException smtpException = new SMTPAddressFailedException(
                    new InternetAddress(RECIPIENT), "RCPT TO", 550, "smtp reply");
            MailSendException noCauseException = new MailSendException(Map.of(new Object(), (Exception) smtpException));
            doThrow(noCauseException).when(mailSender).send(any(MimeMessage.class));

            assertThrows(PermanentMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));
        }

        @Test
        void should_classify_a_mail_parse_exception_as_permanent() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailParseException("malformed message")).when(mailSender).send(any(MimeMessage.class));

            assertThrows(PermanentMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));
        }

        @Test
        void should_classify_a_mail_preparation_exception_as_permanent() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailPreparationException("could not prepare")).when(mailSender).send(any(MimeMessage.class));

            assertThrows(PermanentMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));
        }

        @Test
        void should_classify_an_invalid_address_as_permanent_without_sending() {
            when(mailSender.createMimeMessage()).thenReturn(message);

            assertThrows(PermanentMailDeliveryException.class,
                    () -> mailDeliveryService.send("not an address", "Subject", HTML, TEXT));

            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "a@example.com\r\nBcc: victim@example.com",
                "a@example.com\nBcc: victim@example.com",
                "a@example.com, b@example.com"
        })
        void should_reject_a_recipient_that_would_inject_a_header_or_a_second_recipient_without_sending(String recipient) {
            when(mailSender.createMimeMessage()).thenReturn(message);

            assertThrows(PermanentMailDeliveryException.class,
                    () -> mailDeliveryService.send(recipient, "Subject", HTML, TEXT));

            verify(mailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        void should_classify_a_connect_failure_as_transient() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailSendException("connect failed", new SocketTimeoutException("timeout")))
                    .when(mailSender)
                    .send(any(MimeMessage.class));

            assertThrows(TransientMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));
        }

        @Test
        void should_classify_an_authentication_failure_as_transient() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailAuthenticationException("auth failed")).when(mailSender).send(any(MimeMessage.class));

            assertThrows(TransientMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));
        }
    }

    @Nested
    class Leakage {

        @Test
        void should_not_carry_the_provider_exception_or_its_reply_text_into_a_permanent_exception() throws Exception {
            // A real SMTP server's reply text often echoes the rejected recipient address back, so nothing
            // downstream - including the cause chain - may carry that text where a logger could print it.
            when(mailSender.createMimeMessage()).thenReturn(message);
            String echoedAddress = "victim.real.address@example.com";
            SMTPAddressFailedException smtpException = new SMTPAddressFailedException(
                    new InternetAddress(RECIPIENT), "RCPT TO", 550, "Recipient address rejected: " + echoedAddress);
            doThrow(new MailSendException("send failed", smtpException)).when(mailSender).send(any(MimeMessage.class));

            PermanentMailDeliveryException thrown = assertThrows(PermanentMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));

            assertNull(thrown.getCause());
            assertFalse(thrown.getMessage().contains(echoedAddress));
            assertFalse(thrown.getMessage().contains(RECIPIENT));
        }

        @Test
        void should_not_carry_the_provider_exception_or_its_message_into_a_transient_exception() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailSendException("connect to " + RECIPIENT + " failed", new SocketTimeoutException(RECIPIENT)))
                    .when(mailSender)
                    .send(any(MimeMessage.class));

            TransientMailDeliveryException thrown = assertThrows(TransientMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));

            assertNull(thrown.getCause());
            assertFalse(thrown.getMessage().contains(RECIPIENT));
        }

        @Test
        void should_describe_the_failure_by_type_and_smtp_code_only() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);
            SMTPAddressFailedException smtpException = new SMTPAddressFailedException(
                    new InternetAddress(RECIPIENT), "RCPT TO", 550, "smtp reply");
            doThrow(new MailSendException("send failed", smtpException)).when(mailSender).send(any(MimeMessage.class));

            PermanentMailDeliveryException thrown = assertThrows(PermanentMailDeliveryException.class,
                    () -> mailDeliveryService.send(RECIPIENT, "Subject", HTML, TEXT));

            assertEquals("Permanent mail delivery failure: SMTPAddressFailedException (SMTP 550).", thrown.getMessage());
        }
    }

    static Stream<Arguments> smtpReturnCodes() {
        return Stream.of(
                Arguments.of(550, true),
                Arguments.of(553, true),
                Arguments.of(421, false),
                Arguments.of(451, false));
    }

    /**
     * {@code MimeMessageHelper(message, true, ...)} builds a nested multipart structure even for a single
     * HTML body, so a part has to be found by walking it rather than read off the top-level message.
     */
    private static Part findPart(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return part;
        }

        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();

            for (int i = 0; i < multipart.getCount(); i++) {
                Part matchingPart = findPart(multipart.getBodyPart(i), mimeType);

                if (matchingPart != null) {
                    return matchingPart;
                }
            }
        }

        return null;
    }
}
