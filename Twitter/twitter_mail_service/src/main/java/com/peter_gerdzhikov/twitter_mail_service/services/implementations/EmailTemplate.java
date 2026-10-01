package com.peter_gerdzhikov.twitter_mail_service.services.implementations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.web.util.HtmlUtils;

/**
 * A classpath HTML and text template pair with single-pass {@code {{TOKEN}}} substitution. Both files are
 * loaded and checked against the declared token set in the constructor, so a broken template fails the
 * deployment, not the first email. Callers pass raw values: they are HTML-escaped for the HTML part and
 * stripped of line breaks for the text part, so a value can neither inject markup nor extra header-like
 * lines. Substitution is one pass, so a value that looks like a token stays literal.
 */
public class EmailTemplate {

    private static final String LINE_BREAKS = "[\r\n]";

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{([A-Z_]+)}}");

    private final Set<String> tokens;

    private final String htmlTemplate;

    private final String textTemplate;

    public EmailTemplate(String htmlTemplatePath, String textTemplatePath, Set<String> tokens) {
        this.tokens = Set.copyOf(tokens);
        this.htmlTemplate = loadTemplate(htmlTemplatePath);
        this.textTemplate = loadTemplate(textTemplatePath);
        validateTokens(htmlTemplatePath, htmlTemplate);
        validateTokens(textTemplatePath, textTemplate);
    }

    public RenderedEmail render(Map<String, String> rawValuesByToken) {
        if (!tokens.equals(rawValuesByToken.keySet())) {
            throw new IllegalArgumentException("Expected values for exactly the tokens " + tokens + ".");
        }

        return new RenderedEmail(
                substitute(htmlTemplate, rawValuesByToken, HtmlUtils::htmlEscape),
                substitute(textTemplate, rawValuesByToken, value -> value.replaceAll(LINE_BREAKS, "")));
    }

    private String substitute(
            String template,
            Map<String, String> rawValuesByToken,
            UnaryOperator<String> encoder
    ) {
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuilder rendered = new StringBuilder();

        while (matcher.find()) {
            String encodedValue = encoder.apply(rawValuesByToken.get(matcher.group(1)));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(encodedValue));
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

    private void validateTokens(String templatePath, String templateContent) {
        Set<String> foundTokens = new HashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(templateContent);

        while (matcher.find()) {
            String token = matcher.group(1);

            if (!tokens.contains(token)) {
                throw new IllegalStateException(
                        "Email template '" + templatePath + "' contains an unknown token '{{" + token + "}}'.");
            }

            foundTokens.add(token);
        }

        if (!foundTokens.equals(tokens)) {
            Set<String> missingTokens = new HashSet<>(tokens);
            missingTokens.removeAll(foundTokens);

            throw new IllegalStateException(
                    "Email template '" + templatePath + "' is missing tokens: " + missingTokens + ".");
        }
    }
}
