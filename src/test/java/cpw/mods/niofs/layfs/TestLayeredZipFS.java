package cpw.mods.niofs.layfs;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestLayeredZipFS
{

    @Test
    public void testUriParsingAndAccess() throws URISyntaxException, IOException
    {

        final URI filePathUri = new URI(
          "jij:src/test/resources/dir_in_dir_in_dir.zip!/dir_in_dir.zip!/dir1.zip/"
        );
        final FileSystem zipFS = FileSystems.newFileSystem(filePathUri, new HashMap<>());

        final Path pathInText = zipFS.getPath("masktest.txt");
        final List<String> lines = Files.readAllLines(pathInText);
        final List<String> sourceLines = List.of("dir1");

        assertIterableEquals(sourceLines, lines);
    }

    @Test
    public void testUriConversion() throws URISyntaxException, IOException {
        final URI filePathUri = new URI(
          "jij:" +
            (Path.of("src/test/resources/dir_in_dir_in_dir.zip").toAbsolutePath()
               .toUri().getRawSchemeSpecificPart())
            + "!/dir_in_dir.zip!/dir1.zip!/"
        ).normalize();
        final FileSystem zipFS = FileSystems.newFileSystem(filePathUri, new HashMap<>());

        final Path pathInFS = zipFS.getPath("/");
        final URI uriInFS = pathInFS.toUri();

        assertEquals(filePathUri.toString(), uriInFS.toString());
    }


    @Test
    public void testRelativeUriConversion() throws URISyntaxException, IOException {
        final URI filePathUri = new URI(
          "jij:src/test/resources/dir_in_dir_in_dir.zip!/dir_in_dir.zip!/dir1.zip!/"
        ).normalize();
        final FileSystem zipFS = FileSystems.newFileSystem(filePathUri, new HashMap<>());

        final Path pathInFS = zipFS.getPath("/");
        final URI uriInFS = pathInFS.toUri();

        final URI expectedUri = new URI(
          "jij:" +
            (Path.of("src/test/resources/dir_in_dir_in_dir.zip").toAbsolutePath()
              .toUri().getRawSchemeSpecificPart())
            + "!/dir_in_dir.zip!/dir1.zip!/"
        ).normalize();

        assertEquals(expectedUri.toString(), uriInFS.toString());
    }

    @Test
    public void testSplitResolving() throws URISyntaxException, IOException {
        final URI filePathUri = new URI(
          "jij:src/test/resources/dir_in_dir_in_dir.zip"
        ).normalize();
        final FileSystem zipFS = FileSystems.newFileSystem(filePathUri, new HashMap<>());

        final Path rootPathInFs = zipFS.getPath("/");
        final Path secondLayerZipPath = rootPathInFs.resolve("/dir_in_dir.zip!/");
        final Path thirdLayerZipPath = secondLayerZipPath.resolve("dir1.zip!/");

        assertNotEquals(rootPathInFs.getFileSystem(), secondLayerZipPath.getFileSystem());
        assertNotEquals(secondLayerZipPath.getFileSystem(), thirdLayerZipPath.getFileSystem());
    }

    @Test
    public void testChainedSplitResolving() throws URISyntaxException, IOException {
        final URI filePathUri = new URI(
          "jij:src/test/resources/dir_in_dir_in_dir.zip"
        ).normalize();
        final FileSystem zipFS = FileSystems.newFileSystem(filePathUri, new HashMap<>());

        final Path rootPathInFs = zipFS.getPath("/");
        final Path secondLayerZipPath = rootPathInFs.resolve("/dir_in_dir.zip!/dir1.zip!/");

        assertNotEquals(rootPathInFs.getFileSystem(), secondLayerZipPath.getFileSystem());
    }


    @Test
    public void testPathSplitResolving() throws URISyntaxException, IOException {
        final URI filePathUri = new URI(
          "jij:src/test/resources/dir_in_dir_in_dir.zip"
        ).normalize();
        final FileSystem zipFS = FileSystems.newFileSystem(filePathUri, new HashMap<>());

        final Path rootPathInFs = zipFS.getPath("/");
        final Path secondLayerZipPath = rootPathInFs.resolve(rootPathInFs.getFileSystem().getPath("/dir_in_dir.zip!/"));
        final Path thirdLayerZipPath = secondLayerZipPath.resolve(secondLayerZipPath.getFileSystem().getPath("dir1.zip!/"));

        assertNotEquals(rootPathInFs.getFileSystem(), secondLayerZipPath.getFileSystem());
        assertNotEquals(secondLayerZipPath.getFileSystem(), thirdLayerZipPath.getFileSystem());
    }

    @Test
    public void testChainedPathSplitResolving() throws URISyntaxException, IOException {
        final URI filePathUri = new URI(
          "jij:src/test/resources/dir_in_dir_in_dir.zip"
        ).normalize();
        final FileSystem zipFS = FileSystems.newFileSystem(filePathUri, new HashMap<>());

        final Path rootPathInFs = zipFS.getPath("/");
        final Path secondLayerZipPath = rootPathInFs.resolve(rootPathInFs.getFileSystem().getPath("/dir_in_dir.zip!/dir1.zip!/"));

        assertNotEquals(rootPathInFs.getFileSystem(), secondLayerZipPath.getFileSystem());
    }
}
