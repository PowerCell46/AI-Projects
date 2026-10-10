package com.peter_gerdzhikov.twitter_mail_service.services.implementations.delivery;

import java.util.function.Function;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
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

import com.peter_gerdzhikov.twitter_mail_service.exceptions.PermanentMailDeliveryException;
import com.peter_gerdzhikov.twitter_mail_service.exceptions.TransientMailDeliveryException;
import com.peter_gerdzhikov.twitter_mail_service.services.interfaces.delivery.MailDeliveryService;

@Service
public class MailDeliveryServiceImpl implements MailDeliveryService {

    private static final String LINE_BREAKS = "[\r\n]";

    private static final int PERMANENT_SMTP_REPLY_THRESHOLD = 500;

    private final String fromAddress;

    private final JavaMailSender mailSender;

    public MailDeliveryServiceImpl(@Value("${app.mail.from}") String fromAddress, JavaMailSender mailSender) {
        this.fromAddress = requireValidAddress(fromAddress);
        this.mailSender = mailSender;
    }

    @Override
    public void send(String recipient, String subject, String html, String text) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setValidateAddresses(true);
            helper.setFrom(fromAddress);
            helper.setTo(recipient);
            helper.setSubject(subject.replaceAll(LINE_BREAKS, ""));
            helper.setText(text, html);

            mailSender.send(message);

        } catch (MessagingException | MailException e) {
            throw classify(e);
        }
    }

    /**
     * Fails without the value or the parse failure, which would echo whatever was typed into the log.
     */
    private static String requireValidAddress(String fromAddress) {
        try {
            new InternetAddress(fromAddress, true).validate();

            return fromAddress;

        } catch (AddressException e) {
            throw new IllegalStateException("app.mail.from (MAIL_FROM) must be a valid email address.");
        }
    }

    /**
     * Never logs {@code exception} or passes it on as the delivery exception's cause - an SMTP
     * {@code 5xx}/{@code 4xx} reply routinely echoes the rejected recipient address. {@link #describe}
     * reduces the failure to its exception type and SMTP code only.
     */
    private RuntimeException classify(Exception exception) {
        String description = describe(exception);

        if (isPermanent(exception)) {
            return new PermanentMailDeliveryException("Permanent mail delivery failure: " + description + ".");
        }

        return new TransientMailDeliveryException("Transient mail delivery failure: " + description + ".");
    }

    private boolean isPermanent(Throwable exception) {
        return Boolean.TRUE.equals(walkForFirstMatch(exception, this::classifyPermanence));
    }

    private Boolean classifyPermanence(Throwable current) {
        if (current instanceof MailParseException
                || current instanceof MailPreparationException
                || current instanceof AddressException) {
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
     * Walks {@code exception}'s cause chain and, since a real {@link MailSendException} never sets one - its
     * per-recipient exceptions are only reachable via {@code getMessageExceptions()} - recurses into those
     * too, returning the first non-null result {@code matcher} produces.
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
