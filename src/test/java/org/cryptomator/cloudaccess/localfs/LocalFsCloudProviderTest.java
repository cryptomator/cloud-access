package org.cryptomator.cloudaccess.localfs;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.cryptomator.cloudaccess.api.CloudItemMetadata;
import org.cryptomator.cloudaccess.api.CloudItemType;
import org.cryptomator.cloudaccess.api.CloudProvider;
import org.cryptomator.cloudaccess.api.ProgressListener;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocalFsCloudProviderTest {

	private Path root;
	private CloudProvider provider;

	@BeforeEach
	public void setup(@TempDir Path tempDir) {
		this.root = tempDir;
		this.provider = new LocalFsCloudProvider(tempDir);
	}

	@Test
	@DisplayName("get metadata of /file")
	public void testItemMetadata() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.itemMetadata(Path.of("/file"));
		var metaData = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals("file", metaData.getName());
		Assertions.assertEquals(Path.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(11, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), metaData.getLastModifiedDate().get());
	}

	@Test
	@DisplayName("list /")
	public void testListExhaustively() throws IOException {
		Files.createDirectory(root.resolve("dir"));
		Files.createFile(root.resolve("file"));

		var result = provider.listExhaustively(Path.of("/"));
		var itemList = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertFalse(itemList.getNextPageToken().isPresent());
		MatcherAssert.assertThat(itemList.getItems().stream().map(CloudItemMetadata::getPath).collect(Collectors.toSet()), CoreMatchers.hasItems(Path.of("/file"), Path.of("/dir")));
	}

	@Test
	@DisplayName("read /file (complete)")
	public void testRead() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.read(Path.of("/file"), ProgressListener.NO_PROGRESS_AWARE);
		try (var in = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get())) {
			var allBytes = in.readAllBytes();
			Assertions.assertArrayEquals("hello world".getBytes(), allBytes);
		}
	}

	@Test
	@DisplayName("read /file (bytes 4-6)")
	public void testRandomAccessRead() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());

		var result = provider.read(Path.of("/file"), 4, 3, ProgressListener.NO_PROGRESS_AWARE);
		try (var in = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get())) {
			var allBytes = in.readAllBytes();
			Assertions.assertArrayEquals("o w".getBytes(), allBytes);
		}
	}

	@Test
	@DisplayName("write to /file (non-existing)")
	public void testWriteToNewFile() throws IOException {
		var in = new ByteArrayInputStream("hallo welt".getBytes());
		
		var result = provider.write(Path.of("/file"), false, in, ProgressListener.NO_PROGRESS_AWARE);
		var metaData = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals("file", metaData.getName());
		Assertions.assertEquals(Path.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(10, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), metaData.getLastModifiedDate().get());
	}

	@Test
	@DisplayName("write to /file (already existing)")
	public void testWriteToExistingFile() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());
		var in = new ByteArrayInputStream("hallo welt".getBytes());
		
		var result = provider.write(Path.of("/file"), false, in, ProgressListener.NO_PROGRESS_AWARE);

		var thrown = Assertions.assertThrows(ExecutionException.class, () -> {
			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());
		});

		MatcherAssert.assertThat(thrown.getCause(), CoreMatchers.instanceOf(FileAlreadyExistsException.class));
	}

	@Test
	@DisplayName("write to /file (replace existing)")
	public void testWriteToAndReplaceExistingFile() throws IOException {
		Files.write(root.resolve("file"), "hello world".getBytes());
		var in = new ByteArrayInputStream("hallo welt".getBytes());
		
		var result = provider.write(Path.of("/file"), true, in, ProgressListener.NO_PROGRESS_AWARE);
		var metaData = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals("file", metaData.getName());
		Assertions.assertEquals(Path.of("/file"), metaData.getPath());
		Assertions.assertEquals(CloudItemType.FILE, metaData.getItemType());
		Assertions.assertTrue(metaData.getSize().isPresent());
		Assertions.assertEquals(10, metaData.getSize().get());
		Assertions.assertTrue(metaData.getLastModifiedDate().isPresent());
		Assertions.assertEquals(Files.getLastModifiedTime(root.resolve("file")).toInstant(), metaData.getLastModifiedDate().get());
	}

	@Test
	@DisplayName("create /folder")
	public void testCreateFolder() {
		var result = provider.createFolder(Path.of("/folder"));
		var folder = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals(Path.of("/folder"), folder);
		Assertions.assertTrue(Files.isDirectory(root.resolve("folder")));
	}

	@Test
	@DisplayName("delete /file")
	public void testDeleteFile() throws IOException {
		Files.createFile(root.resolve("file"));

		var result = provider.delete(Path.of("/file"));
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertTrue(Files.notExists(root.resolve("file")));
	}

	@Test
	@DisplayName("delete /folder (recursively)")
	public void testDeleteFolder() throws IOException {
		Files.createDirectory(root.resolve("folder"));
		Files.createFile(root.resolve("folder/file"));

		var result = provider.delete(Path.of("/folder"));
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertTrue(Files.notExists(root.resolve("folder")));
	}

	@Test
	@DisplayName("move /foo -> /bar (non-existing)")
	public void testMoveToNonExisting() throws IOException {
		Files.createFile(root.resolve("foo"));

		var result = provider.move(Path.of("/foo"), Path.of("/bar"), false);
		var moved = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals(Path.of("/bar"), moved);
		Assertions.assertTrue(Files.notExists(root.resolve("foo")));
		Assertions.assertTrue(Files.exists(root.resolve("bar")));
	}

	@Test
	@DisplayName("move /foo -> /bar (already exists)")
	public void testMoveToExisting() throws IOException {
		Files.createFile(root.resolve("foo"));
		Files.createFile(root.resolve("bar"));

		var result = provider.move(Path.of("/foo"), Path.of("/bar"), false);
		var thrown = Assertions.assertThrows(ExecutionException.class, () -> {
			Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());
		});

		MatcherAssert.assertThat(thrown.getCause(), CoreMatchers.instanceOf(FileAlreadyExistsException.class));
	}

	@Test
	@DisplayName("move /foo -> /bar (replace existing)")
	public void testMoveToAndReplaceExisting() throws IOException {
		Files.createFile(root.resolve("foo"));
		Files.createFile(root.resolve("bar"));

		var result = provider.move(Path.of("/foo"), Path.of("/bar"), true);
		var moved = Assertions.assertTimeoutPreemptively(Duration.ofSeconds(1), () -> result.toCompletableFuture().get());

		Assertions.assertEquals(Path.of("/bar"), moved);
		Assertions.assertTrue(Files.notExists(root.resolve("foo")));
		Assertions.assertTrue(Files.exists(root.resolve("bar")));
	}
}