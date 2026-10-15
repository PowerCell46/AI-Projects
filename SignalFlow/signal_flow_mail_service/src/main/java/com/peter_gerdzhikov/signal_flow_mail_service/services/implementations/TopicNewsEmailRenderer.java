package com.peter_gerdzhikov.signal_flow_mail_service.services.implementations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.NewsSchedule;
import com.peter_gerdzhikov.signal_flow_mail_service.DTOs.event.TopicNewsNotificationEventDTO;
import com.peter_gerdzhikov.signal_flow_mail_service.utilities.HtmlToPlainTextConverter;
import com.peter_gerdzhikov.signal_flow_mail_service.utilities.LinkHostAnnotator;

/**
 * Classpath HTML and text templates with single-pass {@code {{TOKEN}}} substitution; both templates are
 * loaded and validated at startup so a broken template fails the deployment, not the first email.
 * {@code DATA} is the only token carrying untrusted AI-generated HTML, so only it runs through
 * {@link #DATA_SANITIZATION_POLICY} - every other HTML token is a plain string, HTML-escaped instead. The
 * text template takes the same sanitized {@code DATA}, converted to plain text, and every other token
 * raw, since a text body has no markup to inject into. In the HTML part each link also shows its real
 * host, since the model can be steered by web pages into a link whose text names another site; the text part
 * already prints the full URL.
 */
@Component
public class TopicNewsEmailRenderer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{([A-Z_]+)}}");

    private static final Set<String> KNOWN_TOKENS = Set.of("TOPIC_NAME", "CATEGORY_NAMES", "NEWS_PERIOD", "GENERATED_AT", "RECIPIENT_EMAIL", "DATA");

    private static final String HTML_CATEGORY_SEPARATOR = " &middot; ";

    private static final String TEXT_CATEGORY_SEPARATOR = " \u00B7 ";

    private static final String HTML_PERIOD_SEPARATOR = " &middot; ";

    private static final String TEXT_PERIOD_SEPARATOR = " \u00B7 ";

    private static final String DAILY_PERIOD_PREFIX = "News for ";

    private static final String EN_DASH = "\u2013";

    private static final DateTimeFormatter NEWS_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter DAY_AND_MONTH_FORMATTER = DateTimeFormatter.ofPattern("d MMMM", Locale.ENGLISH);

    private static final DateTimeFormatter GENERATED_AT_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm zzz", Locale.ENGLISH);

    private static final PolicyFactory DATA_SANITIZATION_POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "ul", "li", "strong", "em", "a")
            .allowAttributes("href").onElements("a")
            .allowUrlProtocols("https")
            .requireRelsOnLinks("noopener", "noreferrer")
            .toFactory();

    private final ZoneId zone;

    private final String htmlTemplate;

    private final String textTemplate;

    public TopicNewsEmailRenderer(
            @Value("${app.mail.zone}") String zone,
            @Value("${app.mail.template.path:classpath:templates/topicNewsEmailTemplate.html}") String htmlTemplatePath,
            @Value("${app.mail.template.text-path:classpath:templates/topicNewsEmailTemplate.txt}") String textTemplatePath
    ) {
        this.zone = ZoneId.of(zone);
        this.htmlTemplate = loadTemplate(htmlTemplatePath);
        this.textTemplate = loadTemplate(textTemplatePath);
        validateTokens(this.htmlTemplate);
        validateTokens(this.textTemplate);
    }

    public RenderedEmail render(TopicNewsNotificationEventDTO event) {
        String sanitizedData = DATA_SANITIZATION_POLICY.sanitize(event.getData());
        String formattedGeneratedAt = event.getGeneratedAt().atZone(zone).format(GENERATED_AT_FORMATTER);

        Map<String, String> htmlValuesByToken = Map.of(
                "TOPIC_NAME", HtmlUtils.htmlEscape(event.getTopicName()),
                "CATEGORY_NAMES", joinHtml(event.getCategoryNames()),
                "NEWS_PERIOD", formatNewsPeriod(event, HTML_PERIOD_SEPARATOR),
                "GENERATED_AT", formattedGeneratedAt,
                "RECIPIENT_EMAIL", HtmlUtils.htmlEscape(event.getEmailAddress()),
                "DATA", LinkHostAnnotator.annotate(sanitizedData)
        );

        Map<String, String> textValuesByToken = Map.of(
                "TOPIC_NAME", stripLineBreaks(event.getTopicName()),
                "CATEGORY_NAMES", joinText(event.getCategoryNames()),
                "NEWS_PERIOD", formatNewsPeriod(event, TEXT_PERIOD_SEPARATOR),
                "GENERATED_AT", formattedGeneratedAt,
                "RECIPIENT_EMAIL", event.getEmailAddress(),
                "DATA", HtmlToPlainTextConverter.convert(sanitizedData)
        );

        return new RenderedEmail(substitute(htmlTemplate, htmlValuesByToken), substitute(textTemplate, textValuesByToken));
    }

    private String formatNewsPeriod(TopicNewsNotificationEventDTO event, String separator) {
        if (event.getSchedule() == NewsSchedule.DAILY) {
            return DAILY_PERIOD_PREFIX + event.getNewsDate().format(NEWS_DATE_FORMATTER);
        }

        return event.getSchedule().getEmailLabel()
                + separator
                + formatDateRange(event.getPeriodStart(), event.getNewsDate());
    }

    private String formatDateRange(LocalDate start, LocalDate end) {
        if (start.equals(end)) {
            return end.format(NEWS_DATE_FORMATTER);
        }

        if (YearMonth.from(start).equals(YearMonth.from(end))) {
            return start.getDayOfMonth() + EN_DASH + end.format(NEWS_DATE_FORMATTER);
        }

        if (start.getYear() == end.getYear()) {
            return start.format(DAY_AND_MONTH_FORMATTER) + " " + EN_DASH + " " + end.format(NEWS_DATE_FORMATTER);
        }

        return start.format(NEWS_DATE_FORMATTER) + " " + EN_DASH + " " + end.format(NEWS_DATE_FORMATTER);
    }

    private String joinHtml(List<String> categoryNames) {
        return categoryNames
                .stream()
                .map(HtmlUtils::htmlEscape)
                .collect(Collectors.joining(HTML_CATEGORY_SEPARATOR));
    }

    private String joinText(List<String> categoryNames) {
        return categoryNames
                .stream()
                .map(this::stripLineBreaks)
                .collect(Collectors.joining(TEXT_CATEGORY_SEPARATOR));
    }

    private String stripLineBreaks(String value) {
        return value.replaceAll("[\r\n]", "");
    }

    private String substitute(String template, Map<String, String> valuesByToken) {
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
