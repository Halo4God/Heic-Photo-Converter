package heicconverter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeicConverterTest {

    @TempDir
    Path tmp;

    private Path touch(Path dir, String name) throws IOException {
        return Files.createFile(dir.resolve(name));
    }

    @Test
    void isHeicIsCaseInsensitiveAndAcceptsHeif() {
        assertTrue(HeicConverter.isHeic(Path.of("a.HEIC")));
        assertTrue(HeicConverter.isHeic(Path.of("b.heif")));
        assertFalse(HeicConverter.isHeic(Path.of("c.jpg")));
    }

    @Test
    void folderSelectionOnlyReturnsHeicFiles() throws IOException {
        touch(tmp, "one.heic");
        touch(tmp, "two.HEIC");
        touch(tmp, "skip.jpg");
        List<Path> found = HeicConverter.collectHeicFiles(List.of(tmp));
        assertEquals(2, found.size());
    }

    @Test
    void mixedSelectionIsDeduplicated() throws IOException {
        Path one = touch(tmp, "one.heic");
        List<Path> found = HeicConverter.collectHeicFiles(List.of(one, tmp, one));
        assertEquals(1, found.size());
    }

    @Test
    void outputGoesInConvertedSubfolderNextToSource() {
        Path source = tmp.resolve("IMG_1.heic");
        assertEquals(tmp.resolve("converted"), HeicConverter.outputDirFor(source));
    }

    @Test
    void existingOutputIsNeverOverwritten() throws IOException {
        Path source = tmp.resolve("IMG_1.heic");
        Path dir = Files.createDirectories(tmp.resolve("converted"));
        touch(dir, "IMG_1.jpg");
        Path target = HeicConverter.uniqueTarget(dir, source, OutputFormat.JPG);
        assertEquals("IMG_1 (1).jpg", target.getFileName().toString());
    }
}
