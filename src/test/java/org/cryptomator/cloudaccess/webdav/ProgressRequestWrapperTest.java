package org.cryptomator.cloudaccess.webdav;

import okhttp3.MediaType;
import okio.Buffer;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ProgressRequestWrapperTest {

    @Test
    public void updateProgressWhenWriteToProgressRequestWrapper() throws IOException {
        final var buffer = new Buffer();
        final var content = new BufferedReader(new InputStreamReader(load(), StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        final var progressListener = Mockito.mock(ProgressListener.class);

        final var spyProgressListener = Mockito.spy(progressListener);

        final var progressRequestWrapper = new ProgressRequestWrapper(InputStreamRequestBody.from(load()), spyProgressListener);

        Assertions.assertEquals(8193, progressRequestWrapper.contentLength());
        Assertions.assertEquals(MediaType.parse("application/octet-stream"), progressRequestWrapper.contentType());

        progressRequestWrapper.writeTo(buffer);
        buffer.flush();

        Assertions.assertEquals(content, buffer.readString(StandardCharsets.UTF_8));

        Mockito.verify(spyProgressListener).onProgress(8192);
        Mockito.verify(spyProgressListener).onProgress(8193);
    }

    private InputStream load() {
        return getClass().getResourceAsStream("/progress-request-text.txt");
    }

}