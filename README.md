# HEIC Converter

A small Java desktop app that converts iPhone `.heic` / `.heif` photos to JPEG or PNG, one file or a whole folder at a time. It uses **FFmpeg** when it can find it (fast) and falls back to **ImageMagick** (slower, but works almost anywhere). Originals are never changed or overwritten.

- Pick files or whole folders, choose JPEG or PNG, click Convert.
- Converts several photos at once; you choose how many with the **Files at once** box.
- Live progress bar, running timer, and a log that shows exactly why any file failed.
- One bad file never stops the rest of the batch.
- Portrait photos come out the right way up.

## Performance

I measured instead of guessing. Test machine: AMD Ryzen 9 9900X (12 cores / 24 threads), Windows, FFmpeg 9.0.2 (gyan.dev "essentials" build).

**FFmpeg vs ImageMagick: 51 iPhone HEIC photos, same machine**

| Method                                            | Total time | Per photo |
| ------------------------------------------------- | ---------- | --------- |
| ImageMagick, one at a time                        | 28.1 s     | 0.55 s    |
| FFmpeg, one at a time                             | 10.1 s     | 0.20 s    |
| FFmpeg, 2 at once                                 | 6.0 s      | 0.12 s    |
| FFmpeg, 4 at once                                 | 4.0 s      | 0.08 s    |
| FFmpeg, 8 at once                                 | 3.1 s      | 0.06 s    |
| FFmpeg with CUDA hardware decoding, one at a time | 32.6 s     | 0.64 s    |

**How many files at once? 400 photos on an SSD, each setting run twice**

| Files at once | Run 1  | Run 2  | Average | Photos per second |
| ------------- | ------ | ------ | ------- | ----------------- |
| 8             | 19.7 s | 18.9 s | 19.3 s  | 20.7              |
| 11            | 17.9 s | 17.7 s | 17.8 s  | 22.5              |
| 12            | 17.5 s | 17.4 s | 17.5 s  | 22.9              |

What I took from the numbers:

- On the same 51 photos, FFmpeg running 8 at once was about **9x faster** than ImageMagick one at a time (28.1 s down to 3.1 s).
- **GPU decoding was slower, not faster** (about 3x slower than plain CPU), so the app does not use it. iPhone HEICs are grids of many small HEVC tiles, and the cost of setting up GPU decoding and copying frames back is bigger than the decoding it saves.
- Returns flatten around the number of physical cores. 12 at once was about 10% faster than 8, and only about 2% faster than 11.
- On the 9900X the default of 11 is within about 2% of the best setting tested.
- Windows was not restarted between runs, so recently read files may have been served from the OS file cache. That is why the second run of each setting is slightly faster.

The default for **Files at once** is half the logical processors minus one on machines with 8 or more (11 on a 24-thread CPU), and one less than the logical processors on smaller machines. Java can't tell physical cores from SMT threads, so this is a conservative guess. You can raise it in the app.

## Requirements

- Java 17 or newer
- **FFmpeg** and/or **ImageMagick**. Either one is enough. With both installed, FFmpeg is tried first and ImageMagick takes over for any file FFmpeg can't convert.
- Maven (only if you want to build the jar and run the tests from the command line)

|                 | FFmpeg (recommended)         | ImageMagick (needs HEIC support)                                                     |
| --------------- | ---------------------------- | ------------------------------------------------------------------------------------ |
| Windows         | `winget install Gyan.FFmpeg` | installer from imagemagick.org, tick "Add application directory to your system path" |
| macOS           | `brew install ffmpeg`        | `brew install imagemagick`                                                           |
| Ubuntu / Debian | `sudo apt install ffmpeg`    | `sudo apt install imagemagick libheif-plugin-libde265`                               |

Use a recent FFmpeg build. iPhone photos store the picture as a grid of tiles, and older FFmpeg builds can't put the tiles back together. I tested with FFmpeg 9.0.2. If FFmpeg fails on a file, the ImageMagick fallback handles it.

**FFmpeg not on your PATH?** Point the app at it with a Java option or an environment variable:

    java -Dffmpeg.path="C:\tools\ffmpeg\bin\ffmpeg.exe" -jar target/heic-converter-1.0.jar

or set `FFMPEG_PATH` to the full path of `ffmpeg.exe`. The app checks `-Dffmpeg.path`, then `FFMPEG_PATH`, then the PATH.

## Run it

With Maven:

    mvn clean package
    java -jar target/heic-converter-1.0.jar

With just a JDK:

    mkdir out
    javac -d out src/main/java/heicconverter/*.java
    java -cp out heicconverter.ConverterApp

In an IDE: import it as a Maven project and run `heicconverter.ConverterApp`. When it starts, the log says which engine is in use, for example `Using: FFmpeg (fallback: ImageMagick)`.

## How to use

1. **Add Files...** to select one or many `.heic` files, or **Add Folder...** to queue every HEIC file directly inside a folder (subfolders are not scanned).
2. Pick **JPEG** or **PNG**.
3. Set **Files at once** (the default is a good starting point).
4. Click **Convert**. Results go into a `converted` folder next to the originals. If a name already exists, the new file becomes `IMG_1 (1).jpg` instead of overwriting.
5. Files that fail stay in the queue, and the log shows the reason.

## How it works

| Class               | Job                                                                                                                          |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `ConversionEngine`  | Interface every converter implements: `convert(source, format)` and `name()`.                                                |
| `FfmpegEngine`      | Converts one file by running FFmpeg (the fast path).                                                                         |
| `HeicConverter`     | The ImageMagick engine, plus the shared helpers: finding HEIC files, building output paths, the `Result` record.             |
| `FallBackEngine`    | Wraps two engines: tries the first, and if it fails on a file, tries the second. If both fail, the error shows both reasons. |
| `FfmpegLocator`     | Finds a working FFmpeg (`-Dffmpeg.path`, then `FFMPEG_PATH`, then PATH).                                                     |
| `ConversionService` | Converts a list of files on a fixed thread pool. Results come back in input order, and one failure never stops the batch.    |
| `OutputFormat`      | JPG / PNG and the options each tool needs for them (quality settings differ between FFmpeg and ImageMagick).                 |
| `ConverterApp`      | The Swing window. Conversion runs in a `SwingWorker`, so the window never freezes.                                           |

Choices worth knowing:

- Both tools are called through `ProcessBuilder`, so there are no extra Java libraries.
- Output names are reserved the moment they are chosen (the empty file is created first), so two photos converting in parallel can never write to the same name.
- FFmpeg is asked for exactly one frame per file, so photos that contain extra images (depth maps, thumbnails) don't create extra output.
- Engines are small interchangeable classes behind one interface, so the service and the tests don't care which tool is doing the work.

## Tests and CI

- `HeicConverterTest` (JUnit 5) covers file detection, folder scanning, de-duplication, the output folder, and non-overwriting names.
- `ConversionServiceTest` covers single and multiple files, and that one failed file doesn't stop the rest of the batch. It uses fake engines, so it needs neither FFmpeg nor ImageMagick.
- Run everything with `mvn test`.
- The `Jenkinsfile` builds, tests, and archives the jar.

## Known limitations and ideas

- Folder scanning goes one level deep.
- FFmpeg or ImageMagick has to be installed separately.
- Ideas: drag and drop, recursive folder scanning, a cancel button, a JPEG quality slider, EXIF handling options.
- Cold-cache hard drive vs SSD benchmark coming soon.
