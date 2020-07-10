package org.cryptomator.cloudaccess.webdav;

import okhttp3.*;
import org.cryptomator.cloudaccess.api.CloudItemList;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.exceptions.*;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static java.lang.String.format;
import static java.net.HttpURLConnection.*;

public class WebDavClient {

    private final WebDavCompatibleHttpClient httpClient;
    private final Path baseUrl;
    private final int HTTP_INSUFFICIENT_STORAGE = 507;

    private enum PROPFIND_DEPTH {
        ZERO("0"),
        ONE("1"),
        INFINITY("infinity");

        private final String value;

        PROPFIND_DEPTH(final String value) {
            this.value = value;
        }
    }

    static class WebDavAuthenticator {
        static WebDavClient createAuthenticatedWebDavClient(final WebDavCredential webDavCredential) throws ServerNotWebdavCompatibleException, UnauthorizedException {
            final var webDavClient = new WebDavClient(new WebDavCompatibleHttpClient(webDavCredential), webDavCredential);

            webDavClient.checkServerCompatibility();
            webDavClient.tryAuthenticatedRequest();

            return webDavClient;
        }
    }

    WebDavClient(final WebDavCompatibleHttpClient httpClient, final WebDavCredential webDavCredential) {
        this.httpClient = httpClient;
        this.baseUrl = webDavCredential.getBaseUrl();
    }

    CloudItemList list(final Path folder) throws BackendException {
        return list(folder, PROPFIND_DEPTH.ONE);
    }

    CloudItemList listExhaustively(Path folder) throws BackendException {
        return list(folder, PROPFIND_DEPTH.INFINITY);
    }

    private CloudItemList list(final Path folder, final PROPFIND_DEPTH propfind_depth) throws BackendException {
        try (final var response = executePropfindRequest(folder, propfind_depth)) {
            checkExecutionSucceeded(response.code());

            final var nodes = getEntriesFromResponse(response);

            return processDirList(nodes);
        } catch (IOException | XmlPullParserException e) {
            throw new BackendException(e);
        }
    }

    CloudItemMetadata itemMetadata(final Path path) throws BackendException {
        try (final var response = executePropfindRequest(path, PROPFIND_DEPTH.ZERO)) {
            checkExecutionSucceeded(response.code());

            final var nodes = getEntriesFromResponse(response);

            return processGet(nodes);
        } catch (IOException | XmlPullParserException e) {
            throw new BackendException(e);
        }
    }

    private Response executePropfindRequest(final Path path, final PROPFIND_DEPTH propfind_depth) throws IOException {
        final var body = "<d:propfind xmlns:d=\"DAV:\">\n" //
                + "<d:prop>\n" //
                + "<d:resourcetype />\n" //
                + "<d:getcontentlength />\n" //
                + "<d:getlastmodified />\n" //
                + "</d:prop>\n" //
                + "</d:propfind>";

        final var builder = new Request.Builder() //
                .method("PROPFIND", RequestBody.create(body, MediaType.parse(body))) //
                .url(absolutePathFrom(path)) //
                .header("DEPTH", propfind_depth.value) //
                .header("Content-Type", "text/xml");

        return httpClient.execute(builder);
    }

    private List<PropfindEntryData> getEntriesFromResponse(final Response response) throws IOException, XmlPullParserException {
        try(final var responseBody = response.body()) {
            return new PropfindResponseParser().parse(responseBody.byteStream());
        }
    }

    private CloudItemMetadata processGet(final List<PropfindEntryData> entryData) {
        entryData.sort(ASCENDING_BY_DEPTH);
        return entryData.size() >= 1 ? entryData.get(0).toCloudItem() : null;
    }

    private final Comparator<PropfindEntryData> ASCENDING_BY_DEPTH = Comparator.comparingInt(PropfindEntryData::getDepth);

    private CloudItemList processDirList(final List<PropfindEntryData> entryData) {
        var result = new CloudItemList(new ArrayList<>());

        if(entryData.isEmpty()) {
            return result;
        }

        entryData.sort(ASCENDING_BY_DEPTH);
        // after sorting the first entry is the parent
        // because it's depth is 1 smaller than the depth
        // ot the other entries, thus we skip the first entry
        for (PropfindEntryData childEntry : entryData.subList(1, entryData.size())) {
            result = result.add(List.of(childEntry.toCloudItem()));
        }
        return result;
    }

    Path move(final Path from, final Path to, boolean replace) throws BackendException {
        final var builder = new Request.Builder() //
                .method("MOVE", null) //
                .url(absolutePathFrom(from)) //
                .header("Destination", absolutePathFrom(to)) //
                .header("Content-Type", "text/xml") //
                .header("Depth", "infinity");

        if(!replace) {
            builder.header("Overwrite", "F");
        }

        try (final var response = httpClient.execute(builder)) {
            if (response.code() == HTTP_PRECON_FAILED) {
                throw new CloudNodeAlreadyExistsException(absolutePathFrom(to));
            }

            checkExecutionSucceeded(response.code());

            return to;
        } catch (IOException e) {
            throw new BackendException(e);
        }
    }

    InputStream read(final Path path, final ProgressListener progressListener) throws BackendException {
        final var getRequest = new Request.Builder() //
                .get() //
                .url(absolutePathFrom(path));
        return read(getRequest, progressListener);
    }

    InputStream read(final Path path, final long offset, final long count, final ProgressListener progressListener) throws BackendException {
        final var getRequest = new Request.Builder() //
                .header("Range", format("bytes=%d-%d", offset, offset + count - 1))
                .get() //
                .url(absolutePathFrom(path));
        return read(getRequest, progressListener);
    }

    private InputStream read(final Request.Builder getRequest, final ProgressListener progressListener) throws BackendException {
        try {
            final var response = httpClient.execute(getRequest);
            final var countingBody = new ProgressResponseWrapper(response.body(), progressListener);
            checkExecutionSucceeded(response.code());
            return countingBody.byteStream();
        } catch (IOException e) {
            throw new BackendException(e);
        }
    }

    CloudItemMetadata write(final Path file, final boolean replace, final InputStream data, final ProgressListener progressListener) throws BackendException {
        if (exists(file) && !replace) {
            throw new CloudNodeAlreadyExistsException("CloudNode already exists and replace is false");
        }

        final var countingBody = new ProgressRequestWrapper(InputStreamRequestBody.from(data), progressListener);
        final var requestBuilder = new Request.Builder()
                .url(absolutePathFrom(file))
                .put(countingBody);

        try (final var response = httpClient.execute(requestBuilder)) {
            checkExecutionSucceeded(response.code());
            return itemMetadata(file);
        } catch (IOException e) {
            throw new BackendException(e);
        }
    }

    private boolean exists(Path path) throws BackendException {
        try {
            return itemMetadata(path) != null;
        } catch (NotFoundException e) {
            return false;
        }
    }

    Path createFolder(final Path path) throws BackendException {
        final var builder = new Request.Builder() //
                .method("MKCOL", null) //
                .url(absolutePathFrom(path));

        try (final var response = httpClient.execute(builder)) {
            checkExecutionSucceeded(response.code());
            return path;
        } catch (IOException e) {
            throw new BackendException(e);
        }
    }

    void delete(final Path path) throws BackendException {
        final var builder = new Request.Builder() //
                .delete() //
                .url(absolutePathFrom(path));

        try (final var response = httpClient.execute(builder)) {
            checkExecutionSucceeded(response.code());
        } catch (IOException e) {
            throw new BackendException(e);
        }
    }

    void checkServerCompatibility() throws ServerNotWebdavCompatibleException  {
        final var optionsRequest = new Request.Builder()
                .method("OPTIONS", null)
                .url(baseUrl.toString());

        try (final var response = httpClient.execute(optionsRequest)) {
            checkExecutionSucceeded(response.code());
            final var containsDavHeader = response.headers().names().contains("DAV");
            if(!containsDavHeader) {
                throw new ServerNotWebdavCompatibleException();
            }
        } catch (IOException e) {
            throw new BackendException(e);
        }
    }

    void tryAuthenticatedRequest() throws UnauthorizedException  {
        try {
            itemMetadata(Path.of("/"));
        } catch (Exception e) {
            if(e instanceof UnauthorizedException) {
                throw e;
            }
        }
    }

    private void checkExecutionSucceeded(final int status) throws BackendException {
        switch (status) {
            case HTTP_UNAUTHORIZED:
                throw new UnauthorizedException();
            case HTTP_FORBIDDEN:
                throw new ForbiddenException();
            case HTTP_NOT_FOUND: // fall through
            case HTTP_CONFLICT: //
                throw new NotFoundException();
            case HTTP_INSUFFICIENT_STORAGE:
                throw new InsufficientStorageException();
        }

        if (status < 199 || status > 300) {
            throw new BackendException("Response code isn't between 200 and 300: " + status);
        }
    }

    private String absolutePathFrom(final Path relativePath) {
        // TODO improve path appending
        return baseUrl.toString() + relativePath.toString();
    }

}