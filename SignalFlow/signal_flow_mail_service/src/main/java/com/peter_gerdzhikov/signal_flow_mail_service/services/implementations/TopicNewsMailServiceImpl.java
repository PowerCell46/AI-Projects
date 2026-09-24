package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;

import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.exceptions.TransientMailDeliveryException;
import com.peter_gerdzhikov.signal_flow_mail_service.services.interfaces.TopicNewsMailService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TopicNewsMailServiceImpl implements TopicNewsMailService {

    private static final String SUBJECT_PREFIX = "[SignalFlow] ";

    private static final int PERMANENT_SMTP_REPLY_THRESHOLD = 500;

    private final String fromAddress;

    private final JavaMailSender mailSender;

    private final TopicNewsEmailRenderer emailRenderer;

    public TopicNewsMailServiceImpl(
            @Value("${app.mail.from}") String fromAddress,
            JavaMailSender mailSender,
            TopicNewsEmailRenderer emailRenderer
    ) {
        this.fromAddress = fromAddress;
        this.mailSender = mailSender;
        this.emailRenderer = emailRenderer;
    }

    @Override
    public void send(TopicNewsNotificationEventDTO event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setValidateAddresses(true);
            helper.setFrom(fromAddress);
            helper.setTo(event.getEmailAddress());
            helper.setSubject(subject(event));
            helper.setText(emailRenderer.render(event), true);

            mailSender.send(message);

        } catch (MessagingException | MailException e) {
            throw classify(e, event);
        }
    }

    private String subject(TopicNewsNotificationEventDTO event) {
        String strippedTopicName = event.getTopicName().replaceAll("[\r\n]", "");
        return SUBJECT_PREFIX + strippedTopicName + " — " + event.getNewsDate();
    }

    /**
     * Never passes {@code exception} itself to the logger or downstream into either delivery exception's
     * cause - a real SMTP server's {@code 5xx}/{@code 4xx} reply text routinely echoes the rejected
     * recipient address (Gmail's bounce format does), which would otherwise reach these logs, and
     * anything further downstream that logs the exception this method returns, through the one field
     * {@code newsId}/{@code userId}-only logging was meant to keep out. {@link #describe} distills the
     * failure down to exception types and SMTP codes only.
     */
    private RuntimeException classify(Exception exception, TopicNewsNotificationEventDTO event) {
        String description = describe(exception);
        if (isPermanent(exception)) {
            log.error("Permanent mail delivery failure for newsId '{}', userId '{}': {}.", event.getNewsId(), event.getUserId(), description);
            return new PermanentMailDeliveryException("Permanent mail delivery failure: " + description + ".", null);
        }

        log.warn("Transient mail delivery failure for newsId '{}', userId '{}': {}.", event.getNewsId(), event.getUserId(), description);
        return new TransientMailDeliveryException("Transient mail delivery failure: " + description + ".", null);
    }

    private boolean isPermanent(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof MailParseException || current instanceof MailPreparationException || current instanceof AddressException) {
                return true;
            }
            if (current instanceof SMTPAddressFailedException failed) {
                return failed.getReturnCode() >= PERMANENT_SMTP_REPLY_THRESHOLD;
            }
            if (current instanceof SMTPSendFailedException failed) {
                return failed.getReturnCode() >= PERMANENT_SMTP_REPLY_THRESHOLD;
            }
            // JavaMailSenderImpl.doSend() builds this via the per-message Map constructor, which never
            // sets a getCause() - the real per-message exception is only reachable through
            // getMessageExceptions(), a separate chain from standard Throwable#getCause().
            if (current instanceof MailSendException mailSendException) {
                for (Exception messageException : mailSendException.getMessageExceptions()) {
                    if (isPermanent(messageException)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String describe(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof SMTPAddressFailedException failed) {
                return current.getClass().getSimpleName() + " (SMTP " + failed.getReturnCode() + ")";
            }
            if (current instanceof SMTPSendFailedException failed) {
                return current.getClass().getSimpleName() + " (SMTP " + failed.getReturnCode() + ")";
            }
            if (current instanceof MailSendException mailSendException) {
                for (Exception messageException : mailSendException.getMessageExceptions()) {
                    String nested = describe(messageException);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }

        return exception.getClass().getSimpleName();
    }
}
