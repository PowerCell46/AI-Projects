package com.peter_gerdzhikov.twitter_api_gateway.utilities.files;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class PathMultipartFileTest {

    @TempDir
    private Path tempDir;

    @Test
    void should_expose_the_file_name_and_content_type() throws Exception {
        Path photo = tempDir.resolve("ana.jpg");
        Files.write(photo, new byte[] {1, 2, 3});
        PathMultipartFile upload = new PathMultipartFile(photo);

        assertThat(upload.getName()).isEqualTo("file");
        assertThat(upload.getOriginalFilename()).isEqualTo("ana.jpg");
        assertThat(upload.getContentType()).isEqualTo("image/jpeg");
    }

    @Test
    void should_report_the_size_and_emptiness() throws Exception {
        Path photo = tempDir.resolve("ana.jpg");
        Files.write(photo, new byte[] {1, 2, 3});
        Path empty = tempDir.resolve("empty.jpg");
        Files.write(empty, new byte[0]);

        assertThat(new PathMultipartFile(photo).getSize()).isEqualTo(3);
        assertThat(new PathMultipartFile(photo).isEmpty()).isFalse();
        assertThat(new PathMultipartFile(empty).isEmpty()).isTrue();
    }

    @Test
    void should_read_the_bytes_and_the_stream() throws Exception {
        Path photo = tempDir.resolve("ana.jpg");
        Files.write(photo, new byte[] {1, 2, 3});
        PathMultipartFile upload = new PathMultipartFile(photo);

        assertThat(upload.getBytes()).isEqualTo(new byte[] {1, 2, 3});
        assertThat(upload.getInputStream().readAllBytes()).isEqualTo(new byte[] {1, 2, 3});
    }

    @Test
    void should_copy_the_file_on_transfer_to() throws Exception {
        Path photo = tempDir.resolve("ana.jpg");
        Files.write(photo, new byte[] {1, 2, 3});
        Path destination = tempDir.resolve("copy.jpg");

        new PathMultipartFile(photo).transferTo(destination.toFile());

        assertThat(Files.readAllBytes(destination)).isEqualTo(new byte[] {1, 2, 3});
    }
}
