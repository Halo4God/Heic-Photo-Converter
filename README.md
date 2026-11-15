# HEIC Converter

A fast, parallel HEIC batch converter for Windows, written in Java with a simple Swing GUI.
It uses **FFmpeg** as the primary engine and automatically falls back to **ImageMagick** if FFmpeg isn't available.

## Features

- **Batch conversion** of whole folders of `.heic` photos
- **FFmpeg engine** (primary) with an **ImageMagick fallback** (`FallbackEngine`)
- **Parallel processing** with an adjustable "Files at once" setting in the GUI
- **Auto-detects FFmpeg** (`FfmpegLocator`), so it doesn't need to be on your PATH
- **Unit tested** with JUnit (the conversion service is tested against a fake engine, so tests don't need real images or FFmpeg)

## Performance

All numbers are from my own machine (Ryzen 9 9900X, 12 cores / 24 threads), with source files on spinning hard drives.

### FFmpeg vs ImageMagick

| Test      | FFmpeg | ImageMagick |
| --------- | ------ | ----------- |
| 51 images | ~10 s  | ~28 s       |

On a larger real-world run, **1,449 HEIC files converted in 99.6 s** with 11 files at once.

### Thread count vs disk speed

To see how much the "Files at once" setting matters, I converted the same 400 files on two different SATA HDDs, three runs per setting (restarting between runs). Times are averages in seconds:

| Files at once | HDD 1  | HDD 2  |
| ------------- | ------ | ------ |
| 8             | 28.1 s | 43.7 s |
| 11            | 31.0 s | 45.1 s |
| 12            | 29.5 s | 45.6 s |

**Takeaways**

- **The drive matters far more than the thread count.** HDD 2 was about 55% slower than HDD 1 at every setting, and no change in thread count closed that gap.
- **More threads didn't help on spinning disks.** 8 at once was as fast as or slightly faster than 11 or 12 on both drives, which points to the disk being the bottleneck rather than the CPU.
- On a fast SSD you may well see different results, so the **Files at once** spinner is left adjustable. If your conversions are slow, try a lower value first when reading from an HDD.

Your results will vary with CPU, disk speed, and file size.

## Requirements

- Java 17+ (adjust to whatever you built with)
- [FFmpeg](https://ffmpeg.org/download.html) (recommended): `ffmpeg.exe` on your PATH, or in a folder the app can find
- [ImageMagick](https://imagemagick.org/) (optional): used as the fallback engine

## Getting started

1. Clone the repo:
   ```bash
   git clone https://github.com/Halo4God/heic-converter.git
   ```
2. Import into Eclipse: **File > Import > Existing Projects into Workspace**
3. Make sure FFmpeg is installed (see above).
4. Run the main class (`heicconverter` package) and pick your source folder.
5. Adjust **Files at once** if you want (see the performance notes above) and hit convert.

## Running the tests

In Eclipse: right-click the project > **Run As > JUnit Test**.

## Project layout

```
src/
└── heicconverter/
    ├── HeicConverter.java       # entry point / ImageMagick lookup
    ├── ConversionService.java   # parallel batch conversion logic
    ├── FfmpegEngine.java        # FFmpeg-based conversion
    ├── FallbackEngine.java      # tries FFmpeg, falls back to ImageMagick
    └── FfmpegLocator.java       # finds ffmpeg on the system
test/
└── heicconverter/
    └── ConversionServiceTest.java
    └── HeicConverterTest.java
```

## Roadmap

- [ ] Progress and error reporting improvements
- [ ] Packaged installer / runnable JAR
- [ ] Image speed tests on a variety of drives

## License

This project is licensed under the [MIT License] (LICENSE).

## Author

([@Halo4God](https://github.com/Halo4God))
