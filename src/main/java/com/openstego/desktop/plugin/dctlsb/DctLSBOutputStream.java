/*
 * Steganography utility to hide messages into cover files
 * Author: Samir Vaidya (mailto:syvaidya@gmail.com)
 * Copyright (c) Samir Vaidya
 */

package com.openstego.desktop.plugin.dctlsb;

import com.openstego.desktop.OpenStegoConfig;
import com.openstego.desktop.OpenStegoException;
import com.openstego.desktop.plugin.template.dct.DCTDataHeader;
import com.openstego.desktop.util.ImageHolder;
import com.openstego.desktop.util.ImageUtil;
import com.openstego.desktop.util.StringUtil;
import com.openstego.desktop.util.dct.DCT;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * OutputStream to embed data into image
 */
public class DctLSBOutputStream extends OutputStream {
    /**
     * Output Image data
     */
    private final ImageHolder image;

    /**
     * Length of the data
     */
    private final int dataLength;

    /**
     * Name of the source data file
     */
    private final String fileName;

    /**
     * Current message bit number
     */
    private int n = 0;

    /**
     * Width of the image rounded to 8 (So that 8x8 blocks can be created from the image)
     */
    private final int imgWidth;

    /**
     * Height of the image rounded to 8 (So that 8x8 blocks can be created from the image)
     */
    private final int imgHeight;

    /**
     * Array to store Y component from YUV colorspace of the image
     */
    private final int[][] y;

    /**
     * Array to store U component from YUV colorspace of the image
     */
    private final int[][] u;

    /**
     * Array to store V component from YUV colorspace of the image
     */
    private final int[][] v;

    /**
     * Object to handle DCT transforms
     */
    private final DCT dct;

    /**
     * Array to store the DCT coefficients for the image
     */
    private final double[][] dcts;

    /**
     * Coordinate hit check class
     */
    private Coordinates coord;

    /**
     * Random number generator
     */
    private final Random rand;

    /**
     * Configuration data
     */
    private final OpenStegoConfig config;

    /**
     * Default constructor
     *
     * @param image      Source image into which data will be embedded
     * @param dataLength Length of the data that would be written to the image
     * @param fileName   Name of the source data file
     * @param config     Configuration data to use while writing
     * @throws OpenStegoException Processing issues
     */
    public DctLSBOutputStream(ImageHolder image, int dataLength, String fileName, OpenStegoConfig config) throws OpenStegoException {
        List<int[][]> yuv;

        if (image == null) {
            throw new IllegalArgumentException("No image provided");
        }

        this.dataLength = dataLength;
        // Actual width of the image
        int actualImgWidth = image.getImage().getWidth();
        // Actual height of the image
        int actualImgHeight = image.getImage().getHeight();
        this.config = config;
        this.fileName = fileName;
        BufferedImage newImg = new BufferedImage(actualImgWidth, actualImgHeight, BufferedImage.TYPE_INT_RGB);
        this.image = new ImageHolder(newImg, image.getMetadata());

        // Calculate width and height rounded to 8
        this.imgWidth = actualImgWidth - (actualImgWidth % DCT.NJPEG);
        this.imgHeight = actualImgHeight - (actualImgHeight % DCT.NJPEG);

        yuv = ImageUtil.getYuvFromImage(image.getImage());
        this.y = yuv.get(0);
        this.u = yuv.get(1);
        this.v = yuv.get(2);
        for (int i = 0; i < actualImgWidth; i++) {
            for (int j = 0; j < actualImgHeight; j++) {
                this.image.getImage().setRGB(i, j, image.getImage().getRGB(i, j));
            }
        }

        this.dct = new DCT();
        this.dct.initDct8x8();
        this.dct.initQuantumJpegLumin();
        this.dcts = new double[DCT.NJPEG][DCT.NJPEG];

        this.rand = new Random(StringUtil.passwordHash(config.getPassword()));
        writeHeader();
    }

    /**
     * Method to write header data to stream
     *
     * @throws OpenStegoException Writing issues
     */
    private void writeHeader() throws OpenStegoException {
        DCTDataHeader header;

        try {
            header = new DCTDataHeader(this.dataLength, this.fileName, this.config);

            if (((header.getHeaderSize() + this.dataLength) * 8) > (this.imgWidth * this.imgHeight / (DCT.NJPEG * DCT.NJPEG))) {
                throw new OpenStegoException(null, DctLSBPlugin.NAMESPACE, DctLSBErrors.IMAGE_SIZE_INSUFFICIENT);
            }
            this.coord = new Coordinates((header.getHeaderSize() + this.dataLength) * 8);
            write(header.getHeaderData());
        } catch (IOException ioEx) {
            throw new OpenStegoException(ioEx);
        }
    }

    /**
     * Implementation of <code>OutputStream.write(int)</code> method
     *
     * @param data Byte to be written
     * @throws IOException Writing issues
     */
    @Override
    public void write(int data) throws IOException {
        int xb;
        int yb;
        int coeffNum;

        for (int count = 0; count < 8; count++) {
            if (this.n >= (this.imgWidth * this.imgHeight * 8)) {
                throw new IOException("Image size insufficient");
            }

            // Randomly select a block, check to get distinct blocks (don't use a block twice)
            do {
                xb = Math.abs(this.rand.nextInt()) % (this.imgWidth / DCT.NJPEG);
                yb = Math.abs(this.rand.nextInt()) % (this.imgHeight / DCT.NJPEG);
            } while (!this.coord.add(xb, yb));

            // Do the forward 8x8 DCT of that block
            this.dct.fwdDctBlock8x8(this.y, xb * DCT.NJPEG, yb * DCT.NJPEG, this.dcts);

            // Randomly select a coefficient. Only accept coefficient in the middle frequency range
            do {
                coeffNum = (Math.abs(this.rand.nextInt()) % (DCT.NJPEG * DCT.NJPEG - 2)) + 1;
            } while (this.dct.isMidFreqCoeff8x8(coeffNum) == 0);

            // Quantize block according to quantization quality parameter
            this.dct.quantize8x8(this.dcts);

            // Robust LSB embedding (interop-bugfixes patch).
            //
            // The original implementation truncated the quantized coefficient
            // toward zero (`(int) coeff`) and then OR'd / AND'd the LSB. That
            // could move the coefficient up to 1.0 unit away from its
            // original value, AND the read path symmetrically truncated the
            // re-quantized coefficient — so any sub-integer drift introduced
            // by the inverse DCT + pixel rounding + forward DCT round trip
            // could flip the recovered LSB.
            //
            // Two surgical changes harden the round trip:
            //   (a) snap the coefficient to the *nearest integer of the
            //       desired parity* (max displacement 0.5 instead of 1.0); and
            //   (b) after embedding, simulate the read by inverse-DCT'ing
            //       this block, re-doing the forward DCT + quantization,
            //       and checking that the recovered LSB matches. If it does
            //       not, push the embedded coefficient by +/-2 (which
            //       preserves parity), restore the rest of the block from
            //       the saved quantized snapshot, and try again, up to
            //       MAX_VERIFY_ATTEMPTS times.
            //
            // The matching read path now uses Math.round() instead of
            // truncation; see DctLSBInputStream#read.
            int desiredBit = ((data >> (7 - count)) & 1);
            int coeffR = coeffNum / DCT.NJPEG;
            int coeffC = coeffNum % DCT.NJPEG;
            double origVal = this.dcts[coeffR][coeffC];

            // Snapshot the entire quantized block. Each verification pass
            // restores from this snapshot before mutating the embedding
            // coefficient and inverse-DCT'ing back to pixels — otherwise
            // the verification fwdDct would clobber the surrounding
            // coefficients and subsequent retries would build on noise.
            double[][] snapshot = new double[DCT.NJPEG][DCT.NJPEG];
            for (int i = 0; i < DCT.NJPEG; i++) {
                System.arraycopy(this.dcts[i], 0, snapshot[i], 0, DCT.NJPEG);
            }

            long target = nearestParity(origVal, desiredBit);
            double[][] verifyDcts = new double[DCT.NJPEG][DCT.NJPEG];
            int[][] simY = new int[DCT.NJPEG][DCT.NJPEG];
            int yOff = xb * DCT.NJPEG;
            int xOff = yb * DCT.NJPEG;
            for (int attempt = 0; attempt <= MAX_VERIFY_ATTEMPTS; attempt++) {
                // Restore the quantized block, then patch in the embedding coefficient.
                for (int i = 0; i < DCT.NJPEG; i++) {
                    System.arraycopy(snapshot[i], 0, this.dcts[i], 0, DCT.NJPEG);
                }
                this.dcts[coeffR][coeffC] = (double) target;

                // Dequantize + inverse DCT writes the block back into Y.
                this.dct.dequantize8x8(this.dcts);
                this.dct.invDctBlock8x8(this.dcts, this.y, yOff, xOff);

                // Simulate the full pixel pipeline that the reader will see:
                // YUV -> RGB (clamped 0..255) -> YUV. The (int) truncations
                // and clamps in this round trip are the dominant source of
                // sub-pixel drift that flips embedded LSBs on read.
                for (int dy = 0; dy < DCT.NJPEG; dy++) {
                    for (int dx = 0; dx < DCT.NJPEG; dx++) {
                        int yy = this.y[yOff + dy][xOff + dx];
                        int uu = this.u[yOff + dy][xOff + dx];
                        int vv = this.v[yOff + dy][xOff + dx];
                        int rr = ImageUtil.pixelRange(yy + 1.140 * vv);
                        int gg = ImageUtil.pixelRange(yy - 0.395 * uu - 0.581 * vv);
                        int bb = ImageUtil.pixelRange(yy + 2.032 * uu);
                        simY[dy][dx] = (int) ((0.299 * rr) + (0.587 * gg) + (0.114 * bb));
                    }
                }

                // Verify by re-running the full read pipeline on the
                // simulated post-roundtrip Y block.
                this.dct.fwdDctBlock8x8(simY, 0, 0, verifyDcts);
                this.dct.quantize8x8(verifyDcts);
                long readback = Math.round(verifyDcts[coeffR][coeffC]);
                // Require the readback both have the right parity AND
                // sit safely away from the half-integer rounding
                // boundary. Coefficients landing within
                // BOUNDARY_MARGIN of a boundary can flip parity due to
                // drift not captured by our YUV/RGB simulation
                // (BufferedImage type conversions, color-space
                // subtleties, downstream rounding differences).
                double frac = Math.abs(verifyDcts[coeffR][coeffC] - (double) readback);
                boolean safelyInCell = frac < 0.5 - BOUNDARY_MARGIN;
                if (((int) (readback & 1L)) == desiredBit && safelyInCell) {
                    break;
                }
                // Drift flipped the recovered LSB. Push the embedded
                // coefficient further away from the boundary in the
                // direction the drift pulled. Step by +/-2 to preserve
                // parity; alternate sign and grow per attempt to cover
                // both directions of drift.
                long delta = readback - target;
                long step;
                if (delta != 0) {
                    step = (delta > 0 ? -2L : 2L) * (long) ((attempt / 2) + 1);
                } else {
                    step = ((attempt & 1) == 0 ? 2L : -2L) * (long) ((attempt / 2) + 1);
                }
                target += step;
            }

            this.n++;
        }
    }

    /** Maximum number of verify-and-adjust attempts per embedded bit. */
    private static final int MAX_VERIFY_ATTEMPTS = 32;

    /**
     * Required distance from a half-integer rounding boundary that the
     * post-roundtrip simulated coefficient must sit at. Coefficients
     * closer than this can flip parity due to drift our YUV/RGB
     * simulation cannot perfectly predict.
     */
    private static final double BOUNDARY_MARGIN = 0.20;

    /**
     * Snap {@code value} to the nearest integer whose least significant bit
     * equals {@code bit}. Maximum displacement is 1.0 (when the rounded
     * value already has the wrong parity); 0.5 in the typical case.
     */
    private static long nearestParity(double value, int bit) {
        long rounded = Math.round(value);
        if ((int) (rounded & 1L) == bit) {
            return rounded;
        }
        // Move one step toward the side of `value` we're closer to so we
        // overshoot by the smaller amount.
        return (value > rounded) ? rounded + 1 : rounded - 1;
    }

    /**
     * Get the image containing the embedded data. Ideally, this should be called after the stream is closed.
     *
     * @param imgType Type of image
     * @return Image data
     * @throws OpenStegoException Processing issues
     */
    public ImageHolder getImage(int imgType) throws OpenStegoException {
        try {
            flush();
        } catch (IOException ioEx) {
            throw new OpenStegoException(ioEx);
        }

        List<int[][]> yuv = new ArrayList<>();
        yuv.add(this.y);
        yuv.add(this.u);
        yuv.add(this.v);

        this.image.setImage(ImageUtil.getImageFromYuv(yuv, imgType));
        return this.image;
    }
}
