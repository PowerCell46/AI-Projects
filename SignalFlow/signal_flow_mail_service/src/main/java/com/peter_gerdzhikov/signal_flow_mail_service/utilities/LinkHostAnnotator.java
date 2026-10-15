package com.peter_gerdzhikov.signal_flow_mail_service.utilities;

import java.net.IDN;
import java.net.MalformedURLException;
import java.net.URI;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

/**
 * Appends the real host after every link of already-sanitized markup, so link text cannot pose as another
 * site: {@code <a href="https://evil.example/x">SignalFlow</a>} reads "SignalFlow (evil.example)". The host is
 * shown in its ASCII (punycode) form so a look-alike Unicode domain is visible too. A link whose host cannot
 * be read is replaced by its plain text.
 */
public final class LinkHostAnnotator {

    private LinkHostAnnotator() {
    }

    public static String annotate(String sanitizedHtml) {
        if (sanitizedHtml == null || sanitizedHtml.isBlank()) {
            return "";
        }

        Document document = Jsoup.parseBodyFragment(sanitizedHtml);
        document.outputSettings().prettyPrint(false);
        for (Element link : document.select("a")) {
            annotate(link);
        }

        return document.body().html();
    }

    private static void annotate(Element link) {
        String host = hostOf(link.attr("href"));
        if (host == null) {
            link.unwrap();
            return;
        }

        link.after(new TextNode(" (" + host + ")"));
    }

    private static String hostOf(String href) {
        try {
            // URL, not URI, because URI.getHost() is null for a non-ASCII host.
            String host = URI
                    .create(href.strip())
                    .toURL()
                    .getHost();
            return host.isEmpty() ? null : IDN.toASCII(host);

        } catch (IllegalArgumentException | MalformedURLException ex) {
            return null;
        }
    }
}
