package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.util.function.Function;

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
     * Never logs {@code exception} itself, or passes it downstream as either delivery exception's cause -
     * an SMTP {@code 5xx}/{@code 4xx} reply routinely echoes the rejected recipient address. {@link
     * #describe} reduces the failure to its exception type and SMTP code only.
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
        return Boolean.TRUE.equals(walkForFirstMatch(exception, this::classifyPermanence));
    }

    private Boolean classifyPermanence(Throwable current) {
        if (current instanceof MailParseException || current instanceof MailPreparationException || current instanceof AddressException) {
            return true;
        }

        Integer smtpReturnCode = smtpReturnCode(current);
        return smtpReturnCode != null ? smtpReturnCode >= PERMANENT_SMTP_REPLY_THRESHOLD : null;
    }

    private String describe(Throwable exception) {
        String smtpDescription = walkForFirstMatch(exception, this::describeSmtpFailure);
        return smtpDescription != null ? smtpDescription : exception.getClass().getSimpleName();
    }

    private String describeSmtpFailure(Throwable current) {
        Integer smtpReturnCode = smtpReturnCode(current);
        return smtpReturnCode != null ? current.getClass().getSimpleName() + " (SMTP " + smtpReturnCode + ")" : null;
    }

    private Integer smtpReturnCode(Throwable exception) {
        if (exception instanceof SMTPAddressFailedException failed) {
            return failed.getReturnCode();
        }
        if (exception instanceof SMTPSendFailedException failed) {
            return failed.getReturnCode();
        }

        return null;
    }

    /**
     * Walks {@code exception}'s cause chain and, since a real {@link MailSendException} never sets one -
     * its per-recipient exceptions are only reachable via {@code getMessageExceptions()} - recurses into
     * those too, returning the first non-null result {@code matcher} produces.
     */
    private <T> T walkForFirstMatch(Throwable exception, Function<Throwable, T> matcher) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            T match = matcher.apply(current);
            if (match != null) {
                return match;
            }

            if (current instanceof MailSendException mailSendException) {
                for (Exception messageException : mailSendException.getMessageExceptions()) {
                    T nested = walkForFirstMatch(messageException, matcher);
                    if (nested != null) {
                        return nested;
                    }
                }
            }
        }

        return null;
    }
}
