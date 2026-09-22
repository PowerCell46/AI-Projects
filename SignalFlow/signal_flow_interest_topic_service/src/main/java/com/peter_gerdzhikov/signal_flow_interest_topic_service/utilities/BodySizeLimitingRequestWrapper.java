package com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.RequestBodyTooLargeException;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/**
 * Caps how much of the body a caller can actually read, for the requests a {@code Content-Length} check
 * cannot catch - a chunked request declares no length, so the only way to know it is oversized is to
 * count the bytes as they arrive.
 */
public class BodySizeLimitingRequestWrapper extends HttpServletRequestWrapper {

    private final long maxBodyBytes;

    public BodySizeLimitingRequestWrapper(HttpServletRequest request, long maxBodyBytes) {
        super(request);
        this.maxBodyBytes = maxBodyBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CountingServletInputStream(super.getInputStream(), maxBodyBytes);
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    private static final class CountingServletInputStream extends ServletInputStream {

        private final long maxBodyBytes;

        private final ServletInputStream delegate;

        private long bytesRead;

        private CountingServletInputStream(ServletInputStream delegate, long maxBodyBytes) {
            this.maxBodyBytes = maxBodyBytes;
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int read = delegate.read();
            if (read != -1) {
                count(1);
            }

            return read;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = delegate.read(buffer, offset, length);
            if (read != -1) {
                count(read);
            }

            return read;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }

        private void count(int justRead) {
            bytesRead += justRead;
            if (bytesRead > maxBodyBytes) {
                throw new RequestBodyTooLargeException();
            }
        }
    }
}
