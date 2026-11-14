package heicconverter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import heicconverter.HeicConverter.Result;

public class ConversionService {

	private final ConversionEngine engine;
	private final int threads;
	
	public ConversionService(ConversionEngine engine) {
		
		this(engine, defaultThreads());
	}
	
	public ConversionService(ConversionEngine engine, int threads) {
		
		this.engine = engine;
		this.threads = Math.max(1, threads);
	}
	
	public static int defaultThreads() {
		
		int logical = Runtime.getRuntime().availableProcessors();
		
		// 8+ logical processors: assuming SMT could be 16, so halve to estimate physical cores, then leave one free.
	    // Smaller machines: just leave one free.
		int threads = (logical >= 8) ? (logical /2 ) - 1 : logical - 1;
		
		return Math.max(1, threads);
	}

	public List<HeicConverter.Result> convertAll(
			
			List<Path> files,
			OutputFormat format,
			Consumer<Result> onResult) {
		
		List<HeicConverter.Result> results = new ArrayList<HeicConverter.Result>();
		
		if (files.isEmpty()) {
			
			return results;
		}
		
		ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, files.size()));
				
		
		try {
			
			List<Future<Result>> futures = new ArrayList<>();
			
			// First loop hands every file to the pool
			for(Path file : files) {
				
				futures.add(pool.submit(() -> convertOne(file, format, onResult)));
			}
			
			// Second loop is to collect the answers in the original order
			for (int i = 0; i < futures.size(); i++) {
				try {
					results.add(futures.get(i).get());
					
				} catch (ExecutionException e) {
					
					results.add(new Result(files.get(i), null, String.valueOf(e.getCause())));
				}
			}
			
		} catch (InterruptedException e) {

			//The caller canceled while we were waiting
			Thread.currentThread().interrupt();
			
			for (int i = results.size(); i < files.size(); i++) {
				
				results.add(new Result(files.get(i), null, "Canceled"));
			}
		
		} finally {
			
			//Shuts down pool so resources dont get used up
			pool.shutdownNow();
			
		}
		
		return results;
	} 

	
	public Result convertOne(Path file, OutputFormat format, Consumer<Result> onResult) {
		
		Result result;
		
		try {
			Path output = engine.convert(file, format);
			
			result = new HeicConverter.Result(file,output,null);
			
		} catch (IOException e) {
			
			result = new HeicConverter.Result(file,null, e.getMessage());
			
		} catch (InterruptedException e) {
			
			Thread.currentThread().interrupt();
			result = new HeicConverter.Result(file, null, "Canceled");
			
		} catch (RuntimeException e) {
			
			result = new HeicConverter.Result(file,null, String.valueOf(e));
			
		}
		
		if (onResult != null) {
			
			onResult.accept(result);
		}
		
		return result;
	}

	
}
