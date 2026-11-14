package heicconverter;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConversionServiceTest {

	private static class FakeEngine implements ConversionEngine {
		
		@Override
		public Path convert(Path source, OutputFormat format) throws IOException {
			
				return Path.of("converted", source.getFileName().toString());
		}
		
		@Override
		public String name() {
			
			return "Fake Engine";
		}
	}
	
	
	private static class FailsOnBadFilesEngine extends FakeEngine {
		
		@Override
		public Path convert(Path source, OutputFormat format) throws IOException {
			
			if (source.getFileName().toString().contains("bad")) {
				throw new IOException("corrupted image");
			}
			
			return super.convert(source, format);
		}
	}
	
	@Test
	void convertFailedFilesOnEngine() {
		
		ConversionService service = new ConversionService(new FailsOnBadFilesEngine());
		
		List<Path> files = List.of(
				
				Path.of("photo.heic"),
				Path.of("bad.heic"),
				Path.of("photo2.heic")
				);
		
		List<HeicConverter.Result> results = service.convertAll(files, OutputFormat.JPG, null);
		
		assertEquals(3, results.size());
		assertTrue(results.get(0).success());
		assertTrue(results.get(2).success());
		assertFalse(results.get(1).success());
		
		assertEquals("corrupted image", results.get(1).error());
		
	}
	
	@Test
	void convertSingleFileSuccessfully() {
		
		ConversionService service = new ConversionService(new FakeEngine());
		
		List<Path> files = List.of(Path.of("photo.heic"));
		
		List<HeicConverter.Result> results = service.convertAll(files, OutputFormat.JPG, null);
		
		assertEquals(1, results.size());
		assertTrue(results.get(0).success());
		assertEquals(Path.of("photo.heic"), results.get(0).source());
		
	}
	
	@Test
	void convertMultipleFileSuccessfully() {
		
		ConversionService service = new ConversionService(new FakeEngine());
		
		List<Path> files = List.of(
				
				Path.of("photo1.heic"),
				Path.of("photo2.heic"),
				Path.of("photo3.heic")
				);
		
		List<HeicConverter.Result> results = service.convertAll(files, OutputFormat.JPG, null);
		
		assertEquals(3, results.size());
		
		assertTrue(results.get(0).success());
		assertTrue(results.get(1).success());
		assertTrue(results.get(2).success());
		
				
	}

}
