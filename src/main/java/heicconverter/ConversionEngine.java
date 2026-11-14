
package heicconverter;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Common contract for image conversion backends.
 */
public interface ConversionEngine {

    /**
     * Converts one image and returns its output path.
     */
    Path convert(Path source, OutputFormat format)
            throws IOException, InterruptedException;

    /**
     * Returns the name of the conversion backend.
     */
    String name();
}
