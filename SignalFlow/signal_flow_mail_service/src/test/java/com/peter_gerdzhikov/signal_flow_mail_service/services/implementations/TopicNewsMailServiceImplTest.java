package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.TransientMailDeliveryException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicNewsMailServiceImplTest {

    private static final String FROM_ADDRESS = "signalflow@example.com";

    @Mock
    private JavaMailSender mailSender;

    private MimeMessage message;

    private TopicNewsMailServiceImpl mailService;

    @BeforeEach
    void setUp() {
        message = new MimeMessage(Session.getInstance(new Properties()));
        mailService = new TopicNewsMailServiceImpl(FROM_ADDRESS, mailSender, new TopicNewsEmailRenderer(
                "Europe/Sofia", "classpath:templates/topicNewsEmailTemplate.html", "classpath:templates/topicNewsEmailTemplate.txt"));
    }

    @Nested
    class Send {

        @Test
        void should_set_recipient_from_subject_and_html_content_type() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);
            TopicNewsNotificationEventDTO event = anEvent("Topic\r\nName", LocalDate.of(2026, 9, 24));

            mailService.send(event);
            message.saveChanges(); // the real JavaMailSenderImpl.send() does this before transmitting

            assertTrue(message.getAllRecipients()[0].toString().contains(event.getEmailAddress()));
            assertTrue(message.getFrom()[0].toString().contains(FROM_ADDRESS));
            assertTrue(message.getSubject().equals("[SignalFlow] TopicName — 2026-09-24"));
            assertTrue(findHtmlPart(message).isMimeType("text/html"));
            assertTrue(findTextPart(message).isMimeType("text/plain"));
        }

        @Test
        void should_round_trip_cyrillic_topic_name_through_utf8() throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);
            TopicNewsNotificationEventDTO event = anEvent("Технологии", LocalDate.of(2026, 9, 24));

            mailService.send(event);
            message.saveChanges();

            assertTrue(message.getSubject().contains("Технологии"));
            assertTrue(((String) findHtmlPart(message).getContent()).contains("Технологии"));
        }
    }

    @Nested
    class Classify {

        @ParameterizedTest
        @MethodSource("com.peter_gerdzhikov.signal_flow_mail_service.services.implementations.TopicNewsMailServiceImplTest#smtpReturnCodes")
        void should_classify_smtp_address_failure_by_return_code(int returnCode, boolean expectedPermanent) throws Exception {
            when(mailSender.createMimeMessage()).thenReturn(message);
            SMTPAddressFailedException smtpException = new SMTPAddressFailedException(
                    new InternetAddress("recipient@example.com"), "RCPT TO", returnCode, "smtp reply");
            doThrow(new MailSendException("send failed", smtpException)).when(mailSender).send(any(MimeMessage.class));

            if (expectedPermanent) {
                assertThrows(PermanentMailDeliveryException.class, () -> mailService.send(anEvent("Topic", LocalDate.now())));
            } else {
                assertThrows(TransientMailDeliveryException.class, () -> mailService.send(anEvent("Topic", LocalDate.now())));
            }
        }

        @Test
        void should_classify_permanent_when_the_real_javamailsenderimpl_shape_has_no_direct_cause() throws Exception {
            // The real JavaMailSenderImpl.doSend() builds MailSendException via its per-message Map
            // constructor, which leaves getCause() null - the real failure is only reachable through
            // getMessageExceptions(). A MailSendException built via the (message, cause) constructor
            // (used in the other tests here) doesn't reproduce this; this test guards that gap.
            when(mailSender.createMimeMessage()).thenReturn(message);
            SMTPAddressFailedException smtpException = new SMTPAddressFailedException(
                    new InternetAddress("recipient@example.com"), "RCPT TO", 550, "smtp reply");
            MailSendException noCauseException = new MailSendException(Map.of(new Object(), (Exception) smtpException));
            doThrow(noCauseException).when(mailSender).send(any(MimeMessage.class));

            assertThrows(PermanentMailDeliveryException.class, () -> mailService.send(anEvent("Topic", LocalDate.now())));
        }

        @Test
        void should_not_carry_the_provider_exception_or_its_message_into_the_thrown_exception() throws Exception {
            // A real SMTP server's reply text often echoes the rejected recipient address back (Gmail's
            // bounce format does) - nothing downstream, including this exception's own cause chain, may
            // carry that text where a future logger could print it.
            when(mailSender.createMimeMessage()).thenReturn(message);
            String secretRecipient = "victim.real.address@example.com";
            SMTPAddressFailedException smtpException = new SMTPAddressFailedException(
                    new InternetAddress("recipient@example.com"), "RCPT TO", 550, "Recipient address rejected: " + secretRecipient);
            doThrow(new MailSendException("send failed", smtpException)).when(mailSender).send(any(MimeMessage.class));

            PermanentMailDeliveryException thrown = assertThrows(PermanentMailDeliveryException.class,
                    () -> mailService.send(anEvent("Topic", LocalDate.now())));

            assertNull(thrown.getCause());
            assertFalse(thrown.getMessage().contains(secretRecipient));
        }

        @Test
        void should_classify_mail_parse_exception_as_permanent() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailParseException("malformed message")).when(mailSender).send(any(MimeMessage.class));

            assertThrows(PermanentMailDeliveryException.class, () -> mailService.send(anEvent("Topic", LocalDate.now())));
        }

        @Test
        void should_classify_connect_failure_as_transient() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailSendException("connect failed", new SocketTimeoutException("timeout")))
                    .when(mailSender).send(any(MimeMessage.class));

            assertThrows(TransientMailDeliveryException.class, () -> mailService.send(anEvent("Topic", LocalDate.now())));
        }

        @Test
        void should_classify_authentication_failure_as_transient() {
            when(mailSender.createMimeMessage()).thenReturn(message);
            doThrow(new MailAuthenticationException("auth failed")).when(mailSender).send(any(MimeMessage.class));

            assertThrows(TransientMailDeliveryException.class, () -> mailService.send(anEvent("Topic", LocalDate.now())));
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
     * {@code MimeMessageHelper(message, true, ...)} builds a nested multipart/mixed > multipart/related
     * structure even for a single HTML body, so the html part has to be found by walking it rather than
     * read off the top-level message.
     */
    private static Part findHtmlPart(Part part) throws Exception {
        return findPart(part, "text/html");
    }

    private static Part findTextPart(Part part) throws Exception {
        return findPart(part, "text/plain");
    }

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

    private static TopicNewsNotificationEventDTO anEvent(String topicName, LocalDate newsDate) {
        return new TopicNewsNotificationEventDTO(
                UUID.randomUUID(),
                UUID.randomUUID(),
                topicName,
                "Category",
                newsDate,
                "<p>Body</p>",
                Instant.parse("2026-09-24T10:00:00Z"),
                UUID.randomUUID(),
                "recipient@example.com");
    }
}
