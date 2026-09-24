package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;

/**
 * Classpath HTML template + single-pass {@code {{TOKEN}}} substitution, no template engine - the
 * inter-cars pattern. The template is loaded and validated once at startup so a broken template fails
 * the deployment immediately rather than the first email it tries to send. Pure and stateless once
 * built - deliberately has no interface, since nothing ever needs a second implementation or a mock of
 * it.
 */
@Component
public class TopicNewsEmailRenderer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{([A-Z_]+)}}");

    private static final Set<String> KNOWN_TOKENS = Set.of(
            "TOPIC_NAME", "CATEGORY_NAME", "NEWS_DATE", "GENERATED_AT", "RECIPIENT_EMAIL", "DATA");

    private static final DateTimeFormatter NEWS_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter GENERATED_AT_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm zzz", Locale.ENGLISH);

    private final ZoneId zone;

    private final String template;

    public TopicNewsEmailRenderer(
            @Value("${app.mail.zone}") String zone,
            @Value("${app.mail.template.path:classpath:templates/topicNewsEmailTemplate.html}") String templatePath
    ) {
        this.zone = ZoneId.of(zone);
        this.template = loadTemplate(templatePath);
        validateTokens(this.template);
    }

    public String render(TopicNewsNotificationEventDTO event) {
        Map<String, String> valuesByToken = Map.of(
                "TOPIC_NAME", HtmlUtils.htmlEscape(event.getTopicName()),
                "CATEGORY_NAME", HtmlUtils.htmlEscape(event.getCategoryName()),
                "NEWS_DATE", event.getNewsDate().format(NEWS_DATE_FORMATTER),
                "GENERATED_AT", event.getGeneratedAt().atZone(zone).format(GENERATED_AT_FORMATTER),
                "RECIPIENT_EMAIL", HtmlUtils.htmlEscape(event.getEmailAddress()),
                "DATA", event.getData());

        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(valuesByToken.get(matcher.group(1))));
        }
        matcher.appendTail(rendered);

        return rendered.toString();
    }

    private String loadTemplate(String templatePath) {
        Resource resource = new DefaultResourceLoader().getResource(templatePath);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        } catch (IOException e) {
            throw new IllegalStateException("Could not load the email template from '" + templatePath + "'.", e);
        }
    }

    private void validateTokens(String templateContent) {
        Set<String> foundTokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(templateContent);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!KNOWN_TOKENS.contains(token)) {
                throw new IllegalStateException("Email template contains an unknown token '{{" + token + "}}'.");
            }
            foundTokens.add(token);
        }

        if (!foundTokens.equals(KNOWN_TOKENS)) {
            Set<String> missingTokens = new HashSet<>(KNOWN_TOKENS);
            missingTokens.removeAll(foundTokens);
            throw new IllegalStateException("Email template is missing tokens: " + missingTokens + ".");
        }
    }
}
