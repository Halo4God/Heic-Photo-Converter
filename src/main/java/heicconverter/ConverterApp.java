package heicconverter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.Timer;

/**
 * Swing front end: queues up HEIC files looks through the folders, 
 * creates the converted folder, pick a format, 
 * choose the amount of threads used, and finally convert.
 */
public class ConverterApp extends JFrame {

	private final ConversionEngine converter; // null if ImageMagick was not found
	private final DefaultListModel<Path> queue = new DefaultListModel<>();
	private final JComboBox<OutputFormat> formatBox = new JComboBox<>(OutputFormat.values());
	private final JButton addFilesButton = new JButton("Add Files...");
	private final JButton addFolderButton = new JButton("Add Folder...");
	private final JButton clearButton = new JButton("Clear");
	private final JButton convertButton = new JButton("Convert");
	private final JSpinner filesAtOnce = new JSpinner(new SpinnerNumberModel(ConversionService.defaultThreads(), 1, Runtime.getRuntime().availableProcessors(), 1));
	private final JProgressBar progress = new JProgressBar();
	private final JTextArea log = new JTextArea(8, 50);
	
	private final JLabel timerLabel = new JLabel("Time : 0.0 s");
	private Timer clock;
	private long startNanos;

	public ConverterApp(ConversionEngine converter) {
		super("HEIC Converter");
		this.converter = converter;
		setDefaultCloseOperation(EXIT_ON_CLOSE);

		addFilesButton.addActionListener(e -> chooseFilesAndAdd());
		addFolderButton.addActionListener(e -> chooseAndAdd(JFileChooser.DIRECTORIES_ONLY));
		clearButton.addActionListener(e -> queue.clear());
		convertButton.addActionListener(e -> startConversion());
		convertButton.setEnabled(converter != null);

		JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
		top.add(addFilesButton);
		top.add(addFolderButton);
		top.add(clearButton);
		top.add(new JLabel("  Convert to:"));
		top.add(formatBox);
		top.add(new JLabel("Files at once"));
		top.add(filesAtOnce);
		top.add(convertButton);

		JScrollPane listScroll = new JScrollPane(new JList<>(queue));
		listScroll.setPreferredSize(new Dimension(600, 220));

		log.setEditable(false);
		progress.setStringPainted(true);
		JPanel bottom = new JPanel(new BorderLayout(0, 6));
		JPanel status = new JPanel(new BorderLayout(8,0));
		status.add(progress, BorderLayout.CENTER);
		status.add(timerLabel, BorderLayout.EAST);
		bottom.add(status, BorderLayout.NORTH);
		
		bottom.add(new JScrollPane(log), BorderLayout.CENTER);

		JPanel content = new JPanel(new BorderLayout(0, 8));
		content.setBorder(new EmptyBorder(8, 8, 8, 8));
		content.add(top, BorderLayout.NORTH);
		content.add(listScroll, BorderLayout.CENTER);
		content.add(bottom, BorderLayout.SOUTH);
		setContentPane(content);
		pack();
		setLocationRelativeTo(null);
	}
	
	// Used for elapsed timer
	private String elapsed() {
	    return String.format("%.1f s", (System.nanoTime() - startNanos) / 1_000_000_000.0);
	}

	// ------------------------------------------------------------ choosing

	private void chooseFilesAndAdd() {
		FileDialog dialog = new FileDialog(this, "Select HEIC Images", FileDialog.LOAD);

		dialog.setMultipleMode(true);

		dialog.setFilenameFilter(
				(dir, name) -> name.toLowerCase().endsWith(".heic") || name.toLowerCase().endsWith(".heif"));

		dialog.setVisible(true);

		File[] selectedFiles = dialog.getFiles();

		if (selectedFiles == null || selectedFiles.length == 0) {
			return;
		}

		List<Path> picked = Arrays.stream(selectedFiles).map(File::toPath).toList();

		addPickedFiles(picked);
	}

	private void addPickedFiles(List<Path> picked) {

		try {
			List<Path> found = HeicConverter.collectHeicFiles(picked);
			int added = 0;
			for (Path path : found) {
				if (!queue.contains(path)) {
					queue.addElement(path);
					added++;
				}
			}
			if (found.isEmpty()) {
				JOptionPane.showMessageDialog(this, "No HEIC files found in that selection.");
			}
			log("Added " + added + " file(s) to the queue.");
		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Could not read selection: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}
	
	/** Checks to see if the user inputed the correct file type, selects them, puts them in an array and are used later**/
	private void chooseAndAdd(int selectionMode) {
		JFileChooser chooser = new JFileChooser();

		boolean selectingFiles = selectionMode == JFileChooser.FILES_ONLY;

		chooser.setMultiSelectionEnabled(selectingFiles);
		chooser.setFileSelectionMode(selectionMode);

		if (selectingFiles) {
			chooser.setFileFilter(new FileNameExtensionFilter("HEIC images (*.heic, *.heif)", "heic", "heif"));
		}

		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
			return;
		}

		List<Path> picked;

		if (selectingFiles) {
			File[] selected = chooser.getSelectedFiles();

			if (selected == null || selected.length == 0) {
				File single = chooser.getSelectedFile();
				if (single == null)
					return;

				picked = List.of(single.toPath());
			} else {
				picked = Arrays.stream(selected).map(File::toPath).toList();
			}

		} else {
			File selected = chooser.getSelectedFile();

			if (selected == null)
				return;

			picked = List.of(selected.toPath());
		}

		try {
			List<Path> found = HeicConverter.collectHeicFiles(picked);

			int added = 0;

			for (Path path : found) {
				if (!queue.contains(path)) {
					queue.addElement(path);
					added++;
				}
			}

			if (found.isEmpty()) {
				JOptionPane.showMessageDialog(this, "No HEIC files found in that selection.");
			}

			log("Added " + added + " file(s) to the queue.");

		} catch (IOException e) {
			JOptionPane.showMessageDialog(this, "Could not read selection: " + e.getMessage(), "Error",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// ---------------------------------------------------------- converting

	private void startConversion() {
		
		// Adds the selected pictures to the Collection, and is in charge of deciding how many threads aka files the user wants to convert at once
		
		if (queue.isEmpty()) {
			JOptionPane.showMessageDialog(this, "Add some HEIC files or a folder first.");
			return;
		}
		
		List<Path> files = Collections.list(queue.elements());
		OutputFormat format = (OutputFormat) formatBox.getSelectedItem();
		
		int threads = ((Number) filesAtOnce.getValue()).intValue();
		ConversionService service = new ConversionService(converter, threads);
		log("Converting " + files.size() + " file(s), " + threads + " at once.");

		setControlsEnabled(false);
		progress.setMaximum(files.size());
		progress.setValue(0);
		
		startNanos = System.nanoTime();
		clock = new Timer(100, e -> timerLabel.setText("Time: " + elapsed()));
		clock.start();

		new SwingWorker<List<HeicConverter.Result>, HeicConverter.Result>() {
			
			@Override
			protected List<HeicConverter.Result> doInBackground() {
				return service.convertAll(files, format, result -> publish(result));
			}

			// Used to dictate whether or not it was able to convert the image
			@Override
			protected void process(List<HeicConverter.Result> chunk) {
				for (HeicConverter.Result r : chunk) {
					progress.setValue(progress.getValue() + 1);
					log(r.success() ? "OK     " + r.source().getFileName() + "  ->  " + r.output()
							: "FAILED " + r.source().getFileName() + ": " + r.error());
				}
			}

			// Final check used to see if anything failed, if the folder was made etc
			@Override
			protected void done() {
				setControlsEnabled(true);
				clock.stop();
				timerLabel.setText("Time: " + elapsed());
				try {
					
					List<HeicConverter.Result> results = get();
					long ok = results.stream().filter(HeicConverter.Result::success).count();
					log("Finished " + results.size() + " file(s) in " + elapsed());
					
					results.stream().filter(HeicConverter.Result::success) .forEach(r -> queue.removeElement(r.source())); // failures stay queued
					
					JOptionPane.showMessageDialog(ConverterApp.this, ok + " of " + results.size() + " file(s) converted into \"" + HeicConverter.OUTPUT_FOLDER_NAME + "\" subfolder(s).");
					
				} catch (Exception e) {
					
					JOptionPane.showMessageDialog(ConverterApp.this, "Conversion failed: " + e, "Error", JOptionPane.ERROR_MESSAGE);
					
				}
			}
		}.execute();
	}

	private void setControlsEnabled(boolean enabled) {
		addFilesButton.setEnabled(enabled);
		addFolderButton.setEnabled(enabled);
		clearButton.setEnabled(enabled);
		formatBox.setEnabled(enabled);
		convertButton.setEnabled(enabled && converter != null);
		filesAtOnce.setEnabled(enabled);
	}

	private void log(String line) {
		log.append(line + "\n");
		log.setCaretPosition(log.getDocument().getLength());
	}

	// ---------------------------------------------------------------- main

	static ConversionEngine chooseEngine(Optional<String> ffmpeg, Optional<String> magick) {
		
		if (ffmpeg.isPresent() && magick.isPresent()) {
			
			return new FallBackEngine(new FfmpegEngine(ffmpeg.get()), new HeicConverter(magick.get()));
		}
		
		if (ffmpeg.isPresent()) {
			
			return new FfmpegEngine(ffmpeg.get());
		}
		
		if (magick.isPresent()) {
			
			return new HeicConverter(magick.get());
		}
		
		return null;
	}
	
	public static void main(String[] args) {

		SwingUtilities.invokeLater(() -> {

			// Tries to find the FFmpeg or the ImageMaginck engine
			ConversionEngine engine = chooseEngine(FfmpegLocator.find(), HeicConverter.findImageMagick());
			
			ConverterApp app = new ConverterApp(engine);
			
			// If on app startup the user does not have any of these this is displayed in a GUI with instructions on how to install them in the different operating systems
			app.setVisible(true);
			if (engine == null) {
				JOptionPane.showMessageDialog(app,
						"Neither FFmpeg or ImageMagick was found, so conversion is disabled.\n\n"
						        + "Install either one (FFmpeg is faster):\n"
						        + "(FFmpeg) Windows: winget install Gyan.FFmpeg\n (ImageMagick) Windows: installer from imagemagick.org, tick “Add to PATH” during install\n"
						        + "(FFmpeg) macOS:   brew install ffmpeg\n (ImageMagick) macOS: brew install imagemagick\n"
						        + "(FFmpeg) Ubuntu:  sudo apt install ffmpeg\n (ImageMagick) Ubuntu: sudo apt install imagemagick libheif-plugin-libde265\n\n"
						        + "FFmpeg not on your PATH? Start the app with\n"
						        + "-Dffmpeg.path=C:\\path\\to\\ffmpeg.exe",
						        "No converter found", 
						JOptionPane.WARNING_MESSAGE);
			} else {
				app.log("Using: " + engine.name());
			}
		});
	}
}
