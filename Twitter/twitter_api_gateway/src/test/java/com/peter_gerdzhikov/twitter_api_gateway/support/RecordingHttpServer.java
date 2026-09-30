package com.peter_gerdzhikov.twitter_api_gateway.support;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import lombok.Value;

/**
 * A stand-in downstream that answers every request with one fixed status and records only a length and a
 * SHA-256 of each body. WireMock cannot serve this purpose for a tweet-sized body: reading a recorded request
 * back through its client fails once the body exceeds Jackson's 20 MB string limit.
 */
public final class RecordingHttpServer {

    private final HttpServer server;

    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();

    private RecordingHttpServer(HttpServer server) {
        this.server = server;
    }

    public static RecordingHttpServer start(int responseStatus) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            RecordingHttpServer recording = new RecordingHttpServer(server);
            server.createContext("/", exchange -> recording.record(exchange, responseStatus));
            server.start();

            return recording;

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String sha256Of(byte[] body) {
        return HexFormat.of().formatHex(newSha256().digest(body));
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
    }

    public List<RecordedRequest> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
    }

    private void record(HttpExchange exchange, int responseStatus) throws IOException {
        MessageDigest digest = newSha256();
        long length;
        try (InputStream body = new DigestInputStream(exchange.getRequestBody(), digest)) {
            length = body.transferTo(OutputStream.nullOutputStream());
        }

        requests.add(new RecordedRequest(
                exchange.getRequestMethod(), exchange.getRequestURI().getPath(), length, HexFormat.of().formatHex(digest.digest())));

        exchange.sendResponseHeaders(responseStatus, -1);
        exchange.close();
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available.", e);
        }
    }

    @Value
    public static class RecordedRequest {

        private final String method;

        private final String path;

        private final long bodyLength;

        private final String bodySha256;
    }
}
