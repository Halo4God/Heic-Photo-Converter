package heicconverter;

import java.io.IOException;
import java.nio.file.Path;

/** 
 * If one engine exists e.g ImageMagick but not FFMPEG then it will use ImageMagick, and vice versa. If both are installed, FFMPEG is the one automatically chosen as 
 * it was the most efficient and fastest in my testing. If neither is installed it will through an error which prevents the user from trying to convert any images.
 * **/
public class FallBackEngine implements ConversionEngine {

	private final ConversionEngine primary;
	private final ConversionEngine fallback;
	
	public FallBackEngine(ConversionEngine primary, ConversionEngine fallback) {
		this.primary = primary;
		this.fallback = fallback;
	}
	
	@Override
	public String name() {
		
		return primary.name() + " (fallback: " + fallback.name() + ")";
		
	}
	
	@Override
	public Path convert(Path source, OutputFormat format) throws IOException, InterruptedException {
		
		try {
			
			return primary.convert(source, format);
			
		} catch (IOException primaryError ) {
	
			try {
				
				return fallback.convert(source, format);
			} catch (IOException fallbackError) {
				
				throw new IOException(primary.name() + ": " + primaryError.getMessage() + " | " + fallback.name() + ": " + fallbackError.getMessage());
			}
		}
	}
}
