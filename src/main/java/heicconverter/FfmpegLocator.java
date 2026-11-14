package heicconverter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 
 * Checks to see if the user has FFMPEG installed through the FFMPEG Default Path if installed using win get otherwise 
 * it needs to be changed manually in VM Arguments make sure in your ConvertedApp.java file you add this in your 
 * VM Arguments make sure to edit the directory depending on where your file path install is for FFMPEG.
 * ________________________________________
 * |-Dffmpeg.path=C:\ffmpeg\bin\ffmpeg.exe |
 * |_______________________________________|
 *
 * **/

public final class FfmpegLocator {

	private FfmpegLocator() {
		
	}
	
	
	public static Optional<String> find() {
		
		List<String> candidates = new ArrayList<>();
		
		String property = System.getProperty("ffmpeg.path");
		
		if (property != null && !property.isBlank()) {
			
			candidates.add(property.trim());
		}
		
		String env = System.getenv("FFMPEG_PATH");
		
		if (env != null && env.isBlank()) {
			
			candidates.add(env.trim());
		}
		
		candidates.add("ffmpeg");
		
		for (String command : candidates) {
			
			if(canRun(command)) {
				return Optional.of(command);
			}
			
		}
		
		return Optional.empty();
		
	}
	
	static boolean canRun(String command) {
		
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
	
}
