package heicconverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Core conversion logic shells out to ImageMagick, because the JDK
 * has no built in HEIC decoder. Converted files go into a "converted" subfolder that
 * is created next to the source image(s).
 */
public class HeicConverter implements ConversionEngine {

    public static final String OUTPUT_FOLDER_NAME = "converted";

    /** Outcome of converting one file. error == null means success. */
    public record Result(Path source, Path output, String error) {
        public boolean success() {
            return error == null;
        }
    }

    private final String magickCommand;

    public HeicConverter(String magickCommand) {
        this.magickCommand = magickCommand;
    }
    
    @Override
    public String name() {
        return "ImageMagick";
    }

    // ---------------------------------------------------------------- setup

    /**
     * Looks for a working ImageMagick command. On Windows only "magick" is
     * tried, because Windows ships an unrelated "convert.exe" disk tool.
     */
    public static Optional<String> findImageMagick() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> candidates = windows ? List.of("magick") : List.of("magick", "convert");
        for (String command : candidates) {
            if (canRun(command)) {
                return Optional.of(command);
            }
        }
        return Optional.empty();
    }

    private static boolean canRun(String command) {
        try {
            Process process = new ProcessBuilder(command, "-version").redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // ------------------------------------------------------ finding inputs

    public static boolean isHeic(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".heic") || name.endsWith(".heif");
    }

    /**
     * Expands a mix of files and folders into a sorted, de-duplicated list of
     * HEIC files. Folders are scanned one level deep (not recursively this may change in a different iteration or if the user wants it to do it).
     */
    public static List<Path> collectHeicFiles(Collection<Path> inputs) throws IOException {
        Set<Path> found = new TreeSet<>();
        for (Path input : inputs) {
            Path path = input.toAbsolutePath().normalize();
            if (Files.isDirectory(path)) {
                try (Stream<Path> children = Files.list(path)) {
                    children.filter(Files::isRegularFile)
                            .filter(HeicConverter::isHeic)
                            .forEach(found::add);
                }
            } else if (Files.isRegularFile(path) && isHeic(path)) {
                found.add(path);
            }
        }
        return new ArrayList<>(found);
    }

    // ----------------------------------------------------------- converting

    /** The "converted" subfolder next to the given source file. */
    public static Path outputDirFor(Path source) {
        return source.toAbsolutePath().getParent().resolve(OUTPUT_FOLDER_NAME);
    }

    /** IMG_1.heic -> IMG_1.jpg, or IMG_1 (1).jpg if that already exists. */
    static synchronized Path uniqueTarget(Path dir, Path source, OutputFormat format) throws IOException {
    	
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        
        Path target = dir.resolve(base + "." + format.extension());
        
        for (int i = 1; Files.exists(target); i++) {
        	
        	try {
        		Files.createFile(target);
        		return target;
        		
        	} catch (FileAlreadyExistsException e ) { 
        		
            target = dir.resolve(base + " (" + i + ")." + format.extension());
            
        	} 
        	
        }
        return target;
    }

    /** Converts one file and returns the path of the new image. */
    @Override
    public Path convert(Path source, OutputFormat format) throws IOException, InterruptedException {
    	
        Path dir = outputDirFor(source);
        Files.createDirectories(dir);
        Path target = uniqueTarget(dir, source, format);

        List<String> command = new ArrayList<>();
        command.add(magickCommand);
        command.add(source.toAbsolutePath() + "[0]"); // first frame only
        command.add("-auto-orient");                   // keep iPhone rotation correct
        command.addAll(format.extraArgs());
        command.add(target.toString());

        try { 
        	
        	Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        	
        	try {
        		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            	int exitCode = process.waitFor();
            	
            	if (exitCode != 0 || Files.size(target) == 0) {
                    throw new IOException(output.isEmpty() ? "ImageMagick exited with code " + exitCode : output); 
                }
                return target;
                    
        	} catch (InterruptedException e) {
        		
        		process.destroyForcibly();
        		throw e;
        	}	
        	
		} catch (IOException | InterruptedException e) {
				
			Files.deleteIfExists(target);
			throw e;
		}
    }
}
