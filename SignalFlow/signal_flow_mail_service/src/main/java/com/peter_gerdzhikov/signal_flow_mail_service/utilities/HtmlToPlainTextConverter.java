package com.peter_gerdzhikov.signal_flow_mail_service.utilities;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Converts already-sanitized {@code <p> <ul> <li> <strong> <em> <a>} markup into a plain-text body for
 * the {@code multipart/alternative} text part - never fed raw, unsanitized HTML.
 */
public final class HtmlToPlainTextConverter {

    private HtmlToPlainTextConverter() {
    }

    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        Document document = Jsoup.parseBodyFragment(html);
        StringBuilder text = new StringBuilder();
        appendChildren(document.body(), text, 0);

        return text.toString().strip();
    }

    private static void appendChildren(Element parent, StringBuilder text, int listDepth) {
        for (Node child : parent.childNodes()) {
            append(child, text, listDepth);
        }
    }

    private static void append(Node node, StringBuilder text, int listDepth) {
        if (node instanceof TextNode textNode) {
            text.append(textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        switch (element.tagName()) {
            case "p" -> {
                appendChildren(element, text, listDepth);
                text.append("\n\n");
            }
            case "ul" -> {
                ensureNewline(text);
                appendChildren(element, text, listDepth + 1);
            }
            case "li" -> {
                text.append("  ".repeat(Math.max(0, listDepth - 1))).append("- ");
                appendChildren(element, text, listDepth);
                text.append("\n");
            }
            case "a" -> {
                String linkText = element.text();
                String href = element.attr("href");
                text.append(href.isBlank() ? linkText : linkText + " (" + href + ")");
            }
            default -> appendChildren(element, text, listDepth);
        }
    }

    private static void ensureNewline(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != '\n') {
            text.append('\n');
        }
    }
}
