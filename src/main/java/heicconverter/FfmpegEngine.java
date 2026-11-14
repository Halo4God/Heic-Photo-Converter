package heicconverter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 
 * This is FFMPEGs Engine which is used alternately then ImageMagick more or less works in the same way but the arguments it uses are a bit different
 * and all serve a different purpose for the purposes of this application. In the future if changes are made or a user wants to customize it further perhaps
 * not wanting to overwrite an empty placeholder file they can remove that command if they so choose.
 * **/
public class FfmpegEngine implements ConversionEngine {

	private final String ffmpegCommand;

	public FfmpegEngine(String ffmpegCommand) {

		this.ffmpegCommand = ffmpegCommand;
	}

	@Override
	public Path convert(Path source, OutputFormat format) throws IOException, InterruptedException {

		Path dir = HeicConverter.outputDirFor(source);
		Files.createDirectories(dir);
		Path target = HeicConverter.uniqueTarget(dir, source, format);

		List<String> command = new ArrayList<>();
		command.add(ffmpegCommand);
		command.add("-nostdin"); // never wait for a keyboard input java just does its thing
		command.add("-y"); // overwrites the empty placeholder file
		command.add("-hide_banner");
		command.add("-loglevel");
		command.add("error");
		command.add("-i");
		command.add(source.toAbsolutePath().toString());
		command.add("-frames:v");
		command.add("1"); // one picture only
		command.add("-update");
		command.add("1"); // single image not a numbered sequence
		command.addAll(format.ffmpegArgs());
		command.add(target.toString());

		try {
			
			Process process = new ProcessBuilder(command).redirectErrorStream(true).start(); 
			
			try {
				String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
				int exitCode = process.waitFor();

				if (exitCode != 0 || Files.size(target) == 0) {
					throw new IOException(output.isEmpty() ? "Ffmpeg exited with code " + exitCode : output);
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

	@Override
	public String name() {

		return "FFmpeg";
	}

}
