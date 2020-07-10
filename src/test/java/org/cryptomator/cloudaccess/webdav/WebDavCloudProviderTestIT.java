package org.cryptomator.cloudaccess.webdav;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.CloudNodeAlreadyExistsException;
import org.junit.Assert;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class WebDavCloudProviderTestIT {

    private final MockWebServer server;
    private final CloudProvider provider;
    private final Path baseUrl;

    private final CloudItemMetadata testFolderDocuments = new CloudItemMetadata("Documents", Path.of("/cloud/remote.php/webdav/Documents"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
    private final CloudItemMetadata testFileManual = new CloudItemMetadata("Nextcloud Manual.pdf", Path.of("/cloud/remote.php/webdav/Nextcloud Manual.pdf"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(6837751L));
    private final CloudItemMetadata testFileIntro = new CloudItemMetadata("Nextcloud intro.mp4", Path.of("/cloud/remote.php/webdav/Nextcloud intro.mp4"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(462413L));
    private final CloudItemMetadata testFilePng = new CloudItemMetadata("Nextcloud.png", Path.of("/cloud/remote.php/webdav/Nextcloud.png"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(37042L));
    private final CloudItemMetadata testFolderPhotos = new CloudItemMetadata("Photos", Path.of("/cloud/remote.php/webdav/Photos"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());

    private final String webDavRequestBody = "<d:propfind xmlns:d=\"DAV:\">\n<d:prop>\n<d:resourcetype />\n<d:getcontentlength />\n<d:getlastmodified />\n</d:prop>\n</d:propfind>";

    public WebDavCloudProviderTestIT() throws IOException, InterruptedException {
        server = new MockWebServer();
        server.start();

        baseUrl = Path.of(String.format("http://%s:%s/cloud/remote.php/webdav", server.getHostName(), server.getPort()));

        final var response = getInterceptedResponse("item-meta-data-response.xml");
        server.enqueue(response);
        server.enqueue(response);

        provider = WebDavCloudProvider.from(WebDavCredential.from(
                baseUrl,
                "foo",
                "bar"
        ));

        server.takeRequest();
        server.takeRequest();
    }

    @Test
    @DisplayName("get metadata of /Nextcloud Manual.pdf")
    public void testItemMetadata() throws InterruptedException {
        server.enqueue(getInterceptedResponse("item-meta-data-response.xml"));

        final var itemMetadata = provider.itemMetadata(Path.of("/Nextcloud Manual.pdf")).toCompletableFuture().join();
        Assert.assertEquals(itemMetadata, testFileManual);

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("0", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/Nextcloud%20Manual.pdf", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
    }

    @Test
    @DisplayName("list /")
    public void testList() throws InterruptedException {
        server.enqueue(getInterceptedResponse("directory-list-response.xml"));

        final var nodeList = provider.list(Path.of("/"), Optional.empty()).toCompletableFuture().join();

        final var expectedList = List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos);

        Assert.assertEquals(nodeList.getItems(), expectedList);
        Assert.assertTrue(nodeList.getNextPageToken().isEmpty());

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("1", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
    }

    @Test
    @DisplayName("list exhaustively /")
    public void testListExhaustively() throws InterruptedException {
        server.enqueue(getInterceptedResponse("directory-list-exhaustively-response.xml"));

        final var nodeList = provider.listExhaustively(Path.of("/")).toCompletableFuture().join();

        final var testFileAbout = new CloudItemMetadata("About.odt", Path.of("/cloud/remote.php/webdav/Documents/About.odt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(77422L));
        final var testFileAboutTxt = new CloudItemMetadata("About.txt", Path.of("/cloud/remote.php/webdav/Documents/About.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(1074L));
        final var testFileFlyer = new CloudItemMetadata("Nextcloud Flyer.pdf", Path.of("/cloud/remote.php/webdav/Documents/Nextcloud Flyer.pdf"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(2529331L));
        final var testFileCoast = new CloudItemMetadata("Coast.jpg", Path.of("/cloud/remote.php/webdav/Photos/Coast.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(819766L));
        final var testFileHummingbird = new CloudItemMetadata("Hummingbird.jpg", Path.of("/cloud/remote.php/webdav/Photos/Hummingbird.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(585219L));
        final var testFileCommunity = new CloudItemMetadata("Nextcloud Community.jpg", Path.of("/cloud/remote.php/webdav/Photos/Nextcloud Community.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(797325L));
        final var testFileNut = new CloudItemMetadata("Nut.jpg", Path.of("/cloud/remote.php/webdav/Photos/Nut.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(955026L));

        final var expectedList = List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos, testFileAbout, testFileAboutTxt, testFileFlyer, testFileCoast, testFileHummingbird, testFileCommunity, testFileNut);

        Assert.assertEquals(nodeList.getItems(), expectedList);
        Assert.assertTrue(nodeList.getNextPageToken().isEmpty());

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("infinity", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
    }

    @Test
    @DisplayName("read /Documents/About.txt (complete)")
    public void testRead() throws InterruptedException {
        server.enqueue(getInterceptedResponse("item-read-response.txt"));

        final var inputStream = provider.read(Path.of("/Documents/About.txt"), ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();
        final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        Assert.assertEquals(content, load("item-read-response.txt"));

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("GET", rq.getMethod());
        Assert.assertNull(rq.getHeader("Range"));
        Assert.assertEquals("/cloud/remote.php/webdav/Documents/About.txt", rq.getPath());
    }

    @Test
    @DisplayName("read /Documents/About.txt (bytes 4-6)")
    public void testRandomAccessRead() throws InterruptedException {
        server.enqueue(getInterceptedResponse("item-partial-read-response.txt"));

        final var inputStream = provider.read(Path.of("/Documents/About.txt"), 4, 2, ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();
        final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        Assert.assertEquals(content, load("item-partial-read-response.txt"));

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("GET", rq.getMethod());
        Assert.assertEquals("bytes=4-5", rq.getHeader("Range"));
        Assert.assertEquals("/cloud/remote.php/webdav/Documents/About.txt", rq.getPath());
    }

    @Test
    @DisplayName("write to /foo.txt (non-existing)")
    public void testWriteToNewFile() throws InterruptedException {
        testWriteToNewFile(true);
    }

    @Test
    @DisplayName("write to /foo.txt (non-existing)")
    public void testWriteToNewFileReplaceIsFalse() throws InterruptedException {
        testWriteToNewFile(false);
    }

    private void testWriteToNewFile(boolean replace) throws InterruptedException {
        server.enqueue(getInterceptedResponse(201, ""));
        server.enqueue(getInterceptedResponse(201, ""));
        server.enqueue(getInterceptedResponse("item-write-response.xml"));

        final var writtenItemMetadata = new CloudItemMetadata("foo.txt", Path.of("/cloud/remote.php/webdav/foo.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 07 Jul 2020 16:55:50 GMT")), Optional.of(8193L));

        final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
        final var cloudItemMetadata = provider.write(Path.of("/foo.txt"), replace, inputStream, ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();

        Assert.assertEquals(writtenItemMetadata, cloudItemMetadata);
        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("0", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());

        rq = server.takeRequest();
        Assert.assertEquals("PUT", rq.getMethod());
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());

        rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("0", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
    }

    @Test
    @DisplayName("write to /file (already existing)")
    public void testWriteToExistingFile() throws InterruptedException {
        server.enqueue(getInterceptedResponse("item-write-response.xml"));

        final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");

        Assertions.assertThrows(CloudNodeAlreadyExistsException.class, () -> {
            final var cloudItemMetadataUsingReplaceFalse = provider.write(Path.of("/foo.txt"), false, inputStream, ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();
            Assert.assertNull(cloudItemMetadataUsingReplaceFalse);
        });

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("0", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
    }

    @Test
    @DisplayName("write to /foo.txt (replace existing)")
    public void testWriteToAndReplaceExistingFile() throws InterruptedException {
        server.enqueue(getInterceptedResponse("item-write-response.xml"));
        server.enqueue(getInterceptedResponse(200, ""));
        server.enqueue(getInterceptedResponse("item-write-response.xml"));

        final var writtenItemMetadata = new CloudItemMetadata("foo.txt", Path.of("/cloud/remote.php/webdav/foo.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 07 Jul 2020 16:55:50 GMT")), Optional.of(8193L));

        final var inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
        final var cloudItemMetadata = provider.write(Path.of("/foo.txt"), true, inputStream, ProgressListener.NO_PROGRESS_AWARE).toCompletableFuture().join();

        Assert.assertEquals(writtenItemMetadata, cloudItemMetadata);

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("0", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());

        rq = server.takeRequest();
        Assert.assertEquals("PUT", rq.getMethod());
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());

        rq = server.takeRequest();
        Assert.assertEquals("PROPFIND", rq.getMethod());
        Assert.assertEquals("0", rq.getHeader("DEPTH"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
        Assert.assertEquals(webDavRequestBody, rq.getBody().readUtf8());
    }

    @Test
    @DisplayName("create /foo")
    public void testCreateFolder() throws InterruptedException {
        server.enqueue(getInterceptedResponse());

        final var path = provider.createFolder(Path.of("/foo")).toCompletableFuture().join();

        Assert.assertEquals(path, Path.of("/foo"));

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("MKCOL", rq.getMethod());
        Assert.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
    }

    @Test
    @DisplayName("delete /foo.txt")
    public void testDeleteFile() throws InterruptedException {
        server.enqueue(getInterceptedResponse());

        provider.delete(Path.of("/foo.txt")).toCompletableFuture().join();

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("DELETE", rq.getMethod());
        Assert.assertEquals("/cloud/remote.php/webdav/foo.txt", rq.getPath());
    }

    @Test
    @DisplayName("delete /foo (recursively)")
    public void testDeleteFolder() throws InterruptedException {
        server.enqueue(getInterceptedResponse());

        provider.delete(Path.of("/foo")).toCompletableFuture().join();

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("DELETE", rq.getMethod());
        Assert.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
    }

    @Test
    @DisplayName("move /foo -> /bar (non-existing)")
    public void testMoveToNonExisting() throws InterruptedException {
        server.enqueue(getInterceptedResponse());

        final var targetPath = provider.move(Path.of("/foo"), Path.of("/bar"), false).toCompletableFuture().join();

        Assert.assertEquals(Path.of("/bar"), targetPath);

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("MOVE", rq.getMethod());
        Assert.assertEquals("infinity", rq.getHeader("Depth"));
        Assert.assertEquals(baseUrl.toString() + "/bar", rq.getHeader("Destination"));
        Assert.assertEquals("F", rq.getHeader("Overwrite"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
    }

    @Test
    @DisplayName("move /foo -> /bar (already exists)")
    public void testMoveToExisting() throws InterruptedException {
        server.enqueue(getInterceptedResponse(412, "item-move-exists-no-replace.xml"));

        Assertions.assertThrows(CloudNodeAlreadyExistsException.class, () -> {
            final var targetPath = provider.move(Path.of("/foo"), Path.of("/bar"), false).toCompletableFuture().join();
            Assert.assertNull(targetPath);
        });

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("MOVE", rq.getMethod());
        Assert.assertEquals("infinity", rq.getHeader("Depth"));
        Assert.assertEquals(baseUrl.toString() + "/bar", rq.getHeader("Destination"));
        Assert.assertEquals("F", rq.getHeader("Overwrite"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
    }

    @Test
    @DisplayName("move /foo -> /bar (replace existing)")
    public void testMoveToAndReplaceExisting() throws InterruptedException {
        server.enqueue(getInterceptedResponse(204, ""));
        final var targetPath = provider.move(Path.of("/foo"), Path.of("/bar"), true).toCompletableFuture().join();

        Assert.assertEquals(Path.of("/bar"), targetPath);

        RecordedRequest rq = server.takeRequest();
        Assert.assertEquals("MOVE", rq.getMethod());
        Assert.assertEquals("infinity", rq.getHeader("Depth"));
        Assert.assertEquals(baseUrl.toString() + "/bar", rq.getHeader("Destination"));
        Assert.assertNull(rq.getHeader("Overwrite"));
        Assert.assertEquals("/cloud/remote.php/webdav/foo", rq.getPath());
    }

    private MockResponse getInterceptedResponse(final String testResource) {
        return getInterceptedResponse(200, load(testResource));
    }

    private MockResponse getInterceptedResponse() {
        return getInterceptedResponse(201, "");
    }

    private MockResponse getInterceptedResponse(int httpCode, final String body) {
        return new MockResponse()
                .setResponseCode(httpCode)
                .setHeader("DAV", "1,2,3, hyperactive-access")
                .setBody(body);
    }

    private String load(String resourceName) {
        final var in = getClass().getResourceAsStream("/webdav-test-responses/" + resourceName);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
    }

}