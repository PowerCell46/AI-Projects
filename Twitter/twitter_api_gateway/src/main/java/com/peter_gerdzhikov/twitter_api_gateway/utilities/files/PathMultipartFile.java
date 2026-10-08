package com.peter_gerdzhikov.twitter_api_gateway.utilities.files;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

/**
 * A file on disk as an upload, so code that has no HTTP request can reuse the services that take one.
 */
@RequiredArgsConstructor
public class PathMultipartFile implements MultipartFile {

    private final Path path;

    @Override
    public String getName() {
        return "file";
    }

    @Override
    public String getOriginalFilename() {
        return path
                .getFileName()
                .toString();
    }

    @Override
    public String getContentType() {
        return URLConnection.guessContentTypeFromName(getOriginalFilename());
    }

    @Override
    public boolean isEmpty() {
        return getSize() == 0;
    }

    @Override
    public long getSize() {
        try {
            return Files.size(path);

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return Files.newInputStream(path);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Files.copy(path, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
