package org.cryptomator.cloudaccess.webdav;

import okhttp3.*;
import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.cryptomator.cloudaccess.api.exceptions.CloudNodeAlreadyExistsException;
import org.cryptomator.cloudaccess.api.exceptions.UnauthorizedException;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

class WebDavClientTest {

    private final WebDavCompatibleHttpClient webDavCompatibleHttpClient = Mockito.mock(WebDavCompatibleHttpClient.class);
    private WebDavClient webDavClient;

    private final Path baseUrl = Path.of("https://www.nextcloud.com/cloud/remote.php/webdav");

    private final CloudItemMetadata testFolderDocuments = new CloudItemMetadata("Documents", Path.of("/cloud/remote.php/webdav/Documents"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());
    private final CloudItemMetadata testFileManual = new CloudItemMetadata("Nextcloud Manual.pdf", Path.of("/cloud/remote.php/webdav/Nextcloud Manual.pdf"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(6837751L));
    private final CloudItemMetadata testFileIntro = new CloudItemMetadata("Nextcloud intro.mp4", Path.of("/cloud/remote.php/webdav/Nextcloud intro.mp4"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(462413L));
    private final CloudItemMetadata testFilePng = new CloudItemMetadata("Nextcloud.png", Path.of("/cloud/remote.php/webdav/Nextcloud.png"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(37042L));
    private final CloudItemMetadata testFolderPhotos = new CloudItemMetadata("Photos", Path.of("/cloud/remote.php/webdav/Photos"), CloudItemType.FOLDER, Optional.empty(), Optional.empty());

    @BeforeEach
    public void setup() {
        final var webDavCredential = WebDavCredential.from(baseUrl, "foo", "bar");
        webDavClient = new WebDavClient(webDavCompatibleHttpClient, webDavCredential);
    }

    @Test
    @DisplayName("get metadata of /Nextcloud Manual.pdf")
    public void testItemMetadata() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "item-meta-data-response.xml"));

        final var itemMetadata = webDavClient.itemMetadata(Path.of("/Nextcloud Manual.pdf"));

        Assert.assertEquals(testFileManual, itemMetadata);
    }

    @Test
    @DisplayName("list /")
    public void testList() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "directory-list-response.xml"));

        final var nodeList = webDavClient.list(Path.of("/"));

        final var expectedList = List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos);

        Assert.assertEquals(expectedList, nodeList.getItems());

        Assert.assertTrue(nodeList.getNextPageToken().isEmpty());
    }

    @Test
    @DisplayName("list exhaustively /")
    public void testListExhaustively() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "directory-list-exhaustively-response.xml"));

        final var nodeList = webDavClient.listExhaustively(Path.of("/"));

        final var testFileAbout = new CloudItemMetadata("About.odt", Path.of("/cloud/remote.php/webdav/Documents/About.odt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(77422L));
        final var testFileAboutTxt = new CloudItemMetadata("About.txt", Path.of("/cloud/remote.php/webdav/Documents/About.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(1074L));
        final var testFileFlyer = new CloudItemMetadata("Nextcloud Flyer.pdf", Path.of("/cloud/remote.php/webdav/Documents/Nextcloud Flyer.pdf"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(2529331L));
        final var testFileCoast = new CloudItemMetadata("Coast.jpg", Path.of("/cloud/remote.php/webdav/Photos/Coast.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(819766L));
        final var testFileHummingbird = new CloudItemMetadata("Hummingbird.jpg", Path.of("/cloud/remote.php/webdav/Photos/Hummingbird.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(585219L));
        final var testFileCommunity = new CloudItemMetadata("Nextcloud Community.jpg", Path.of("/cloud/remote.php/webdav/Photos/Nextcloud Community.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(797325L));
        final var testFileNut = new CloudItemMetadata("Nut.jpg", Path.of("/cloud/remote.php/webdav/Photos/Nut.jpg"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 19 Feb 2020 10:24:12 GMT")), Optional.of(955026L));

        final var expectedList = List.of(testFolderDocuments, testFileManual, testFileIntro, testFilePng, testFolderPhotos, testFileAbout, testFileAboutTxt, testFileFlyer, testFileCoast, testFileHummingbird, testFileCommunity, testFileNut);

        Assert.assertEquals(expectedList, nodeList.getItems());
        Assert.assertTrue(nodeList.getNextPageToken().isEmpty());
    }

    @Test
    @DisplayName("read /Documents/About.txt (complete)")
    public void testRead() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "item-read-response.txt"));

        final var inputStream = webDavClient.read(Path.of("/Documents/About.txt"), ProgressListener.NO_PROGRESS_AWARE);
        final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        Assert.assertEquals(load("item-read-response.txt"), content);
    }

    @Test
    @DisplayName("read /Documents/About.txt (bytes 4-6)")
    public void testRandomAccessRead() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any())).thenReturn(getInterceptedResponse(baseUrl, "item-partial-read-response.txt"));

        final var inputStream = webDavClient.read(Path.of("/Documents/About.txt"), 4, 2, ProgressListener.NO_PROGRESS_AWARE);
        final var content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));

        Assert.assertEquals(load("item-partial-read-response.txt"), content);
    }

    @Test
    @DisplayName("write to /foo.txt (non-existing)")
    public void testWriteToNewFile() throws IOException {
        testWriteToNewFile(true);
    }

    @Test
    @DisplayName("write to /foo.txt (non-existing)")
    public void testWriteToNewFileReplaceIsFalse() throws IOException {
        testWriteToNewFile(false);
    }

    private void testWriteToNewFile(boolean replace) throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl))
                .thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"));

        final var writtenItemMetadata = new CloudItemMetadata("foo.txt", Path.of("/cloud/remote.php/webdav/foo.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 07 Jul 2020 16:55:50 GMT")), Optional.of(8193L));

        InputStream inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
        final var cloudItemMetadata = webDavClient.write(Path.of("/foo.txt"), replace, inputStream, ProgressListener.NO_PROGRESS_AWARE);

        Assert.assertEquals(writtenItemMetadata, cloudItemMetadata);
    }

    @Test
    @DisplayName("write to /file (already existing)")
    public void testWriteToExistingFile() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"));

        InputStream inputStream = getClass().getResourceAsStream("/progress-request-text.txt");

        Assertions.assertThrows(CloudNodeAlreadyExistsException.class, () -> {
            final var cloudItemMetadataUsingReplaceFalse = webDavClient.write(Path.of("/foo.txt"), false, inputStream, ProgressListener.NO_PROGRESS_AWARE);
            Assert.assertNull(cloudItemMetadataUsingReplaceFalse);
        });
    }

    @Test
    @DisplayName("write to /foo.txt (replace existing)")
    public void testWriteToAndReplaceExistingFile() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"))
                .thenReturn(getInterceptedResponse(baseUrl, "item-write-response.xml"));

        final var writtenItemMetadata = new CloudItemMetadata("foo.txt", Path.of("/cloud/remote.php/webdav/foo.txt"), CloudItemType.FILE, Optional.of(TestUtil.toInstant("Thu, 07 Jul 2020 16:55:50 GMT")), Optional.of(8193L));

        InputStream inputStream = getClass().getResourceAsStream("/progress-request-text.txt");
        final var cloudItemMetadata = webDavClient.write(Path.of("/foo.txt"), true, inputStream, ProgressListener.NO_PROGRESS_AWARE);

        Assert.assertEquals(writtenItemMetadata, cloudItemMetadata);
    }

    @Test
    @DisplayName("create /foo")
    public void testCreateFolder() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl));

        final var path = webDavClient.createFolder(Path.of("/foo"));

        Assert.assertEquals(Path.of("/foo"), path);
    }

    @Test
    @DisplayName("delete /foo.txt")
    public void testDeleteFile() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl));

        webDavClient.delete(Path.of("/foo.txt"));
    }

    @Test
    @DisplayName("delete /foo (recursively)")
    public void testDeleteFolder() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl));

        webDavClient.delete(Path.of("/foo"));
    }

    @Test
    @DisplayName("move /foo -> /bar (non-existing)")
    public void testMoveToNonExisting() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl));

        final var targetPath = webDavClient.move(Path.of("/foo"), Path.of("/bar"), false);

        Assert.assertEquals(Path.of("/bar"), targetPath);
    }

    @Test
    @DisplayName("move /foo -> /bar (already exists)")
    public void testMoveToExisting() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl, 412, "item-move-exists-no-replace.xml"));

        Assertions.assertThrows(CloudNodeAlreadyExistsException.class, () -> {
            final var targetPath = webDavClient.move(Path.of("/foo"), Path.of("/bar"), false);
            Assert.assertNull(targetPath);
        });
    }

    @Test
    @DisplayName("move /foo -> /bar (replace existing)")
    public void testMoveToAndReplaceExisting() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl, 204, ""));

        final var targetPath = webDavClient.move(Path.of("/foo"), Path.of("/bar"), true);

        Assert.assertEquals(Path.of("/bar"), targetPath);
    }

    @Test
    @DisplayName("check if server recognizes WebDAV servers")
    public void testCheckServerCompatibility() throws IOException {
        final var davResponse = new Response.Builder()
                .request(new Request.Builder()
                        .url(baseUrl.toString())
                        .build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .addHeader("DAV", "1, 3, extended-mkcol")
                .body(ResponseBody.create("", MediaType.parse("application/json; charset=utf-8")))
                .message("")
                .build();

        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(davResponse)
                .thenReturn(getInterceptedResponse(baseUrl));

        webDavClient.checkServerCompatibility();

        Assertions.assertThrows(ServerNotWebdavCompatibleException.class, () -> webDavClient.checkServerCompatibility());
    }

    @Test
    @DisplayName("check if client can authenticate against server")
    public void testTryAuthenticatedRequest() throws IOException {
        Mockito.when(webDavCompatibleHttpClient.execute(ArgumentMatchers.any()))
                .thenReturn(getInterceptedResponse(baseUrl, "authentication-response.xml"))
                .thenReturn(getInterceptedResponse(baseUrl, 401, ""));

        webDavClient.tryAuthenticatedRequest();
        Assertions.assertThrows(UnauthorizedException.class, () -> webDavClient.tryAuthenticatedRequest());
    }

    private Response getInterceptedResponse(final Path path, final String testResource) {
        return getInterceptedResponse(path, 200, load(testResource));
    }

    private Response getInterceptedResponse(final Path path) {
        return getInterceptedResponse(path, 201, "");
    }

    private Response getInterceptedResponse(final Path path, int httpCode, final String body) {
        return new Response.Builder()
                .request(new Request.Builder()
                        .url(path.toString())
                        .build())
                .protocol(Protocol.HTTP_1_1)
                .code(httpCode)
                .body(ResponseBody.create(body, MediaType.parse("application/json; charset=utf-8")))
                .message("")
                .build();
    }

    private String load(String resourceName) {
        final var in = getClass().getResourceAsStream("/webdav-test-responses/" + resourceName);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
    }
}