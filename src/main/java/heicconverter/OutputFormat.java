package heicconverter;

import java.util.List;

/** Image formats the converter can write. */
public enum OutputFormat {

    JPG("JPEG (.jpg)", "jpg", List.of("-quality", "92"), List.of("-q:v", "2")),
    PNG("PNG (.png)", "png", List.of(), List.of());
	

    private final String label;
    private final String extension;
    private final List<String> extraArgs;
	private final List<String> ffmpegArgs;

    OutputFormat(String label, String extension, List<String> extraArgs, List<String> ffmpegArgs) {
        this.label = label;
        this.extension = extension;
        this.extraArgs = extraArgs;
        this.ffmpegArgs = ffmpegArgs;
    }

    /** File extension without the dot. */
    public String extension() {
        return extension;
    }

    /** Extra ImageMagick arguments for this format (e.g JPEG quality). */
    public List<String> extraArgs() {
        return extraArgs;
    }
    
    /** Extra FFmpeg arguments for this format (e.g JPEG quality, 2 = high/best). */
    public List<String> ffmpegArgs() {
    	return ffmpegArgs;
    	
    }

    @Override
    public String toString() {
        return label; // what the combo box shows
    }
}
