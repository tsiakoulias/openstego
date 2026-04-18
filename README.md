# OpenStego
OpenStego is a steganography application that provides two functionalities:

1. Data Hiding: It can hide any data within an image file.

2. Watermarking: Watermarking image files with an invisible signature. It can be used to detect unauthorized file copying.

## Usage

### For GUI:
Use the menu shortcut if you used the Windows installer. For standalone Windows app-image builds, launch the bundled executable directly. For the legacy zip distribution, use the bundled launcher script.
```
OpenStego.exe                (standalone Windows package)
```
```
openstego.bat                (Windows)
```
```
./openstego.sh               (Linux / MacOS)
```

### For command line interface:
Refer to [online documentation](https://www.openstego.com/cmdline.html).

## Development
Fork the repository, clone it locally and execute following to build it fully:
```
gradlew clean dist           (Windows)
```
```
./gradlew clean dist         (Linux / MacOS)
```

To build the standalone Windows executable package with a bundled runtime:
```
gradlew clean distWinExe
```
This creates `build\jpackage\OpenStego\OpenStego.exe` for the GUI and `build\jpackage\OpenStego\OpenStegoCLI.exe` for the command line. To create a portable zip of that package:
```
gradlew clean distWinPortable
```

*Note:* This repository's Gradle 7.5.1 wrapper should be run on JDK 17. Standalone Windows packaging uses `jpackage`; if you want the bundled runtime to come from another JDK (for example JDK 25), keep Gradle on JDK 17 and pass `-PjpackageHome=<path-to-jdk>`. The Windows installer task still needs [Inno Setup](https://jrsoftware.org/isdl.php) and `iscc.exe` on `PATH`, but it now packages the standalone runtime image rather than a bare jar. If you don't want to generate the Windows installer, you can skip the same using following command:
```
./gradlew clean dist -x distWin
```

## Author
Samir Vaidya (samir [at] openstego.com)

## Homepage
https://www.openstego.com

## License
GNU General Public License 2.0 (GPL) (see ```LICENSE``` file)

## Acknowledgement
The digital watermarking code in this product is based on the code provided by Peter Meerwald. Refer to his excellent thesis on [watermarking](http://www.cosy.sbg.ac.at/~pmeerw/Watermarking/): Peter Meerwald, Digital Image Watermarking in the Wavelet Transfer Domain, Master's Thesis, Department of Scientific Computing, University of Salzburg, Austria, January 2001.
