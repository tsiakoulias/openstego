@app.name@ v@app.version@
================

@app.name@ is a steganography application that provides two functionalities:
  1. Data Hiding: It can hide any data within an image file.
  2. Watermarking: Watermarking image files with an invisible signature. It
     can be used to detect unauthorized file copying.

This Windows package is standalone and includes its own Java runtime. No
separate Java installation is required on the target machine.

For GUI:
  OpenStego.exe

For command line interface:
  OpenStegoCLI.exe <command> [options]

To display command help:
  OpenStegoCLI.exe --help

The first argument must be a command. Valid commands are:
  embed, --embed                Embed message into cover file
  extract, --extract            Extract message from stego file
  gensig, --gensig              Generate a signature for watermarking
  embedmark, --embedmark        Watermark a cover file using signature
  checkmark, --checkmark        Checks for watermark presence in the file
  algorithms, --algorithms      List supported steganography algorithms
  readformats, --readformats    List supported formats for cover file
  writeformats, --writeformats  List supported formats for stego file
  help, --help                  Display help

AUTHOR:
-------
  @author.name@ (@author.mail@)
  Copyright (c) 2007-@time.year@

SEE ALSO:
---------
  Project homepage: @homepage.url@

LICENSE:
--------
  GNU General Public License 2.0 (GPL) (see LICENSE file)
