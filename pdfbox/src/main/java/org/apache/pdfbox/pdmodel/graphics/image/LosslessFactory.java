/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.pdmodel.graphics.image;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.*;

/**
 * Factory for creating a PDImageXObject containing a lossless compressed image.
 *
 * @author Tilman Hausherr
 */
public final class LosslessFactory
{
    private LosslessFactory()
    {
    }
    
    /**
     * Creates a new lossless encoded Image XObject from a Buffered Image.
     *
     * @param document the document where the image will be created
     * @param image the buffered image to embed
     * @return a new Image XObject
     * @throws IOException if something goes wrong
     */
    public static PDImageXObject createFromImage(PDDocument document, BufferedImage image)
            throws IOException
    {
        if ((image.getType() == BufferedImage.TYPE_BYTE_GRAY && image.getColorModel().getPixelSize() <= 8)
                || (image.getType() == BufferedImage.TYPE_BYTE_BINARY && image.getColorModel().getPixelSize() == 1))
        {
            return createFromGrayImage(image, document);
        }
        else
        {
            return createFromRGBImage(image, document);
        }      
    }

    // grayscale images need one color per sample
    private static PDImageXObject createFromGrayImage(BufferedImage image, PDDocument document)
            throws IOException
    {
        int height = image.getHeight();
        int width = image.getWidth();
        int[] rgbLineBuffer = new int[width];
        int bpc = image.getColorModel().getPixelSize();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(((width*bpc/8)+(width*bpc%8 != 0 ? 1:0))*height);
        MemoryCacheImageOutputStream mcios = new MemoryCacheImageOutputStream(baos);
        for (int y = 0; y < height; ++y)
        {
            for (int pixel : image.getRGB(0, y, width, 1, rgbLineBuffer, 0, width))
            {
                mcios.writeBits(pixel & 0xFF, bpc);
            }

            int bitOffset = mcios.getBitOffset();
            if (bitOffset != 0)
            {
                mcios.writeBits(0, 8 - bitOffset);
            }
        }
        mcios.flush();
        mcios.close();
        return prepareImageXObject(document, baos.toByteArray(),
                image.getWidth(), image.getHeight(), bpc, PDDeviceGray.INSTANCE);
    }

    private static PDImageXObject createFromRGBImage(BufferedImage image, PDDocument document) throws IOException
    {
        int height = image.getHeight();
        int width = image.getWidth();
        int[] rgbLineBuffer = new int[width];
        int bpc = 8;
        PDDeviceColorSpace deviceColorSpace = PDDeviceRGB.INSTANCE;
        byte[] imageData = new byte[width * height * 3];
        int byteIdx = 0;
        int alphaByteIdx = 0;
        int alphaBitPos = 7;
        int transparency = image.getTransparency();
        int apbc = transparency == Transparency.BITMASK ? 1 : 8;
        byte[] alphaImageData;
        if (transparency != Transparency.OPAQUE)
        {
            alphaImageData = new byte[((width * apbc / 8) + (width * apbc % 8 != 0 ? 1 : 0)) * height];
        }
        else
        {
            alphaImageData = new byte[0];
        }
        for (int y = 0; y < height; ++y)
        {
            for (int pixel : image.getRGB(0, y, width, 1, rgbLineBuffer, 0, width))
            {
                imageData[byteIdx++] = (byte) ((pixel >> 16) & 0xFF);
                imageData[byteIdx++] = (byte) ((pixel >> 8) & 0xFF);
                imageData[byteIdx++] = (byte) (pixel & 0xFF);
                if (transparency != Transparency.OPAQUE)
                {
                    // we have the alpha right here, so no need to do it separately
                    // as done prior April 2018
                    if (transparency == Transparency.BITMASK)
                    {
                        // write a bit
                        alphaImageData[alphaByteIdx] |= ((pixel >> 24) & 1) << alphaBitPos;
                        if (--alphaBitPos < 0)
                        {
                            alphaBitPos = 7;
                            ++alphaByteIdx;
                        }
                    }
                    else
                    {
                        // write a byte
                        alphaImageData[alphaByteIdx++] = (byte) ((pixel >> 24) & 0xFF);
                    }
                }
            }

            // skip boundary if needed
            if (transparency == Transparency.BITMASK && alphaBitPos != 7)
            {
                alphaBitPos = 7;
                ++alphaByteIdx;
            }
        }
        PDImageXObject pdImage = prepareImageXObject(document, imageData,
                image.getWidth(), image.getHeight(), bpc, deviceColorSpace);      
        if (transparency != Transparency.OPAQUE)
        {
            PDImageXObject pdMask = prepareImageXObject(document, alphaImageData,
                    image.getWidth(), image.getHeight(), apbc, PDDeviceGray.INSTANCE);
            pdImage.getCOSObject().setItem(COSName.SMASK, pdMask);
        }
        return pdImage;
    }

    /**
     * Create a PDImageXObject while making a decision whether not to 
     * compress, use Flate filter only, or Flate and LZW filters.
     * 
     * @param document The document.
     * @param byteArray array with data.
     * @param width the image width
     * @param height the image height
     * @param bitsPerComponent the bits per component
     * @param initColorSpace the color space
     * @return the newly created PDImageXObject with the data compressed.
     * @throws IOException 
     */
    private static PDImageXObject prepareImageXObject(PDDocument document, 
            byte [] byteArray, int width, int height, int bitsPerComponent, 
            PDColorSpace initColorSpace) throws IOException
    {
        //pre-size the output stream to half of the input
        ByteArrayOutputStream baos = new ByteArrayOutputStream(byteArray.length/2);

        Filter filter = FilterFactory.INSTANCE.getFilter(COSName.FLATE_DECODE);
        filter.encode(new ByteArrayInputStream(byteArray), baos, new COSDictionary(), 0);

        ByteArrayInputStream encodedByteStream = new ByteArrayInputStream(baos.toByteArray());
        return new PDImageXObject(document, encodedByteStream, COSName.FLATE_DECODE, 
                width, height, bitsPerComponent, initColorSpace);
    }

    /**
     * Tries to compress the image using a predictor.
     * @return the image or null if it is not possible to encoded the image (e.g. not supported
     * raster format etc.)
     */
    private static PDImageXObject compressImageWithPredictor(PDDocument document, BufferedImage image)
    {
        return null;
    }

    private static PDImageXObject compressAs16BitRGBPredictorEncoded(PDDocument document, BufferedImage image) 
            throws IOException 
    {
        final int BYTES_PER_PIXEL = 6;
        final int COMPONENTS_PER_PIXEL = 3;

        final int height = image.getHeight();
        final int width = image.getWidth();
        // The rows have 1-byte encoding marker and width*BYTES_PER_PIXEL pixel-bytes
        final int dataRowByteCount = width * BYTES_PER_PIXEL + 1;
        byte[] dataRawRowNone = new byte[dataRowByteCount];
        byte[] dataRawRowSub = new byte[dataRowByteCount];
        byte[] dataRawRowUp = new byte[dataRowByteCount];
        byte[] dataRawRowAverage = new byte[dataRowByteCount];
        byte[] dataRawRowPaeth = new byte[dataRowByteCount];

        // Write the encoding markers
        dataRawRowNone[0] = 0;
        dataRawRowSub[0] = 1;
        dataRawRowUp[0] = 2;
        dataRawRowAverage[0] = 3;
        dataRawRowPaeth[0] = 4;

        Raster imageRaster = image.getRaster();

        int writerPtr;
        short[] prevRow = new short[width * COMPONENTS_PER_PIXEL];
        short[] transferRow = new short[width * COMPONENTS_PER_PIXEL];
        // pre-size the output stream to half of the maximum size
		ByteArrayOutputStream stream = new ByteArrayOutputStream(height * width * BYTES_PER_PIXEL / 2);
        Deflater deflater = new Deflater(9);
        DeflaterOutputStream zip = new DeflaterOutputStream(stream, deflater);

        for (int i = 0; i < height; i++) 
        {
            imageRaster.getDataElements(0, i, width, 1, transferRow);

            writerPtr = 1;
            byte[] aValues = new byte[BYTES_PER_PIXEL];
            byte[] cValues = new byte[BYTES_PER_PIXEL];
            byte[] bValues = new byte[BYTES_PER_PIXEL];
            byte[] xValues = new byte[BYTES_PER_PIXEL];
            byte[] tmpValues = new byte[BYTES_PER_PIXEL];
            for (int j = 0; j < transferRow.length;) 
            {
                short valbR = prevRow[j];
                short valR = transferRow[j++];
                short valbG = prevRow[j];
                short valG = transferRow[j++];
                short valbB = prevRow[j];
                short valB = transferRow[j++];

                xValues[0] = (byte) ((valR & 0xFF00) >> 8);
                xValues[1] = (byte) (valR & 0xFF);
                xValues[2] = (byte) ((valG & 0xFF00) >> 8);
                xValues[3] = (byte) (valG & 0xFF);
                xValues[4] = (byte) ((valB & 0xFF00) >> 8);
                xValues[5] = (byte) (valB & 0xFF);

                bValues[0] = (byte) ((valbR & 0xFF00) >> 8);
                bValues[1] = (byte) (valbR & 0xFF);
                bValues[2] = (byte) ((valbG & 0xFF00) >> 8);
                bValues[3] = (byte) (valbG & 0xFF);
                bValues[4] = (byte) ((valbB & 0xFF00) >> 8);
                bValues[5] = (byte) (valbB & 0xFF);

                writeEncodedRowsIntoRowBuffer(dataRawRowNone, dataRawRowSub, dataRawRowUp, dataRawRowAverage, 
                        dataRawRowPaeth, writerPtr, aValues, cValues, bValues, xValues, tmpValues, BYTES_PER_PIXEL);

                /*
                 * We shift the values into the prev / upper left values for the next
                 * pixel
                 */
                System.arraycopy(xValues, 0, aValues, 0, BYTES_PER_PIXEL);
                System.arraycopy(bValues, 0, cValues, 0, BYTES_PER_PIXEL);

                writerPtr += BYTES_PER_PIXEL;
            }

            byte[] rowToWrite = chooseDataRowToWrite(dataRawRowNone, dataRawRowSub, dataRawRowUp, dataRawRowAverage, dataRawRowPaeth);

            /*
             * Write and compress the row as long it is hot (CPU cache wise)
             */
            zip.write(rowToWrite, 0, rowToWrite.length);

            {
                /*
                 * We swap prev and transfer row, so that we have the prev row for the next 
                 * row.
                 */
                short[] temp = prevRow;
                prevRow = transferRow;
                transferRow = temp;
            }
        }
        zip.close();
        deflater.end();

        return preparePredictorPDImage(document, image, stream, 16);
    }
    
    private static PDImageXObject preparePredictorPDImage(PDDocument document, BufferedImage image,
            ByteArrayOutputStream stream, int bitsPerComponent) throws IOException
    {
        int height = image.getHeight();
        int width = image.getWidth();

		ColorSpace srcCspace = image.getColorModel().getColorSpace();
        PDColorSpace pdColorSpace = srcCspace.getType() == ColorSpace.TYPE_CMYK ? PDDeviceRGB.INSTANCE : PDDeviceCMYK.INSTANCE;

        // Encode the image profile if the image has one
		if (srcCspace instanceof ICC_ColorSpace)
		{
            ICC_ColorSpace icc_colorSpace = (ICC_ColorSpace) srcCspace;
            ICC_Profile profile = icc_colorSpace.getProfile();
            // We only encode a colorspace if it is not sRGB
			if (profile != ICC_Profile.getInstance(ColorSpace.CS_sRGB))
			{
				PDICCBased pdProfile = new PDICCBased(document);
				OutputStream outputStream = pdProfile.getPDStream().createOutputStream(COSName.FLATE_DECODE);
				outputStream.write(profile.getData());
				outputStream.close();
				pdProfile.getPDStream().getCOSObject().setInt(COSName.N, srcCspace.getNumComponents());
			}
        }

		PDImageXObject imageXObject = new PDImageXObject(document, new ByteArrayInputStream(stream.toByteArray()),
				COSName.FLATE_DECODE, width, height, bitsPerComponent, pdColorSpace);

		COSDictionary decodeParms = new COSDictionary();
		decodeParms.setItem(COSName.BITS_PER_COMPONENT, COSInteger.get(bitsPerComponent));
		decodeParms.setItem(COSName.PREDICTOR, COSInteger.get(15));
		decodeParms.setItem(COSName.COLUMNS, COSInteger.get(width));
		decodeParms.setItem(COSName.COLORS, COSInteger.get(srcCspace.getNumComponents()));
		imageXObject.getCOSObject().setItem(COSName.DECODE_PARMS, decodeParms);
		return imageXObject;
    }

    /**
     * Write the current pixel using different row encoding
     */
    private static void writeEncodedRowsIntoRowBuffer(byte[] dataRawRowNone, byte[] dataRawRowSub, byte[] dataRawRowUp, 
            byte[] dataRawRowAverage, byte[] dataRawRowPaeth, 
            int writerPtr, byte[] aValues, byte[] cValues, byte[] bValues, byte[] xValues, byte[] tmpValues, 
            int bytesPerPixel)
    {
        writeBytes(dataRawRowNone, writerPtr, xValues, bytesPerPixel);

        pngFilterSub(xValues, aValues, tmpValues, bytesPerPixel);
        writeBytes(dataRawRowSub, writerPtr, tmpValues, bytesPerPixel);

        pngFilterUp(xValues, bValues, tmpValues, bytesPerPixel);
        writeBytes(dataRawRowUp, writerPtr, tmpValues, bytesPerPixel);

        pngFilterAverage(xValues, bValues, tmpValues, bytesPerPixel);
        writeBytes(dataRawRowAverage, writerPtr, tmpValues, bytesPerPixel);

        pngFilterPaeth(xValues, aValues, bValues, cValues, tmpValues, bytesPerPixel);
        writeBytes(dataRawRowPaeth, writerPtr, tmpValues, bytesPerPixel);
    }

    /**
	 * We look which row encoding is the "best" one, ie. has the lowest sum. We
	 * don't implement anything fancier to choose the right row encoding. This is
	 * just the recommend algorithm in the spec. The get the perfect encoding you
	 * would need to do a brute force check how all the different encoded 
     * rows compress in the zip stream together. You have would have to check 
     * 5*image-height permutations... 
	 * 
	 * @return the "best" row encoding of the row encodings
	 */
    private static byte[] chooseDataRowToWrite(byte[] dataRawRowNone, byte[] dataRawRowSub, byte[] dataRawRowUp,
            byte[] dataRawRowAverage, byte[] dataRawRowPaeth) 
    {
        byte[] rowToWrite = dataRawRowNone;
        long estCompressSum = estCompressSum(dataRawRowNone);
        long estCompressSumSub = estCompressSum(dataRawRowSub);
        long estCompressSumUp = estCompressSum(dataRawRowUp);
        long estCompressSumAvg = estCompressSum(dataRawRowAverage);
        long estCompressSumPaeth = estCompressSum(dataRawRowPaeth);
        if (estCompressSum > estCompressSumSub) 
        {
            rowToWrite = dataRawRowSub;
            estCompressSum = estCompressSumSub;
        }
        if (estCompressSum > estCompressSumUp) 
        {
            rowToWrite = dataRawRowUp;
            estCompressSum = estCompressSumUp;
        }
        if (estCompressSum > estCompressSumAvg) 
        {
            rowToWrite = dataRawRowAverage;
            estCompressSum = estCompressSumAvg;
        }
        if (estCompressSum > estCompressSumPaeth) 
        {
            rowToWrite = dataRawRowPaeth;
        }
        return rowToWrite;
    }

    private static void writeBytes(byte[] b, int offset, byte[] source, int length) 
    {
        System.arraycopy(source, 0, b, offset, length);
    }

    /*
     * PNG Filters, see https://www.w3.org/TR/PNG-Filters.html
     */
    private static void pngFilterSub(byte[] x, byte[] a, byte[] result, int length) 
    {
        assert x.length >= length;
        assert a.length >= length;
        for (int i = 0; i < length; i++) 
        {
            int r = (x[i] & 0xff) - (a[i] & 0xff);
            result[i] = (byte) (r);
        }
    }

    private static void pngFilterUp(byte[] x, byte[] b, byte[] res, int length) 
    {
        // Same as pngFilterSub, just called with the prior row
        pngFilterSub(x, b, res, length);
    }

    private static void pngFilterAverage(byte[] x, byte[] b, byte[] result, int length) 
    {
        assert x.length >= length;
        assert b.length >= length;
        for (int i = 0; i < length; i++) 
        {
            int xV = (x[i] & 0xff);
            int bV = (b[i] & 0xff);
            int r = xV - ((xV - bV) / 2);
            result[i] = (byte) (r);
        }
    }

    private static void pngFilterPaeth(byte[] x, byte[] a, byte[] b, byte[] c, byte[] result, int length) 
    {
    	assert x.length >= length;
        assert a.length >= length;
        assert b.length >= length;
        assert c.length >= length;
        for (int i = 0; i < length; i++) 
        {
            int xV = (x[i] & 0xff);
            int aV = (a[i] & 0xff);
            int bV = (b[i] & 0xff);
            int cV = (c[i] & 0xff);

            int p = aV + bV - cV;
            int pa = Math.abs(p - aV);
            int pb = Math.abs(p - bV);
            int pc = Math.abs(p - cV);
            final int Pr;
            if (pa <= pb && pa <= pc)
                Pr = aV;
            else if (pb <= pc)
                Pr = bV;
            else
                Pr = cV;

            int r = xV - Pr;
            result[i] = (byte) (r);
        }
    }

    private static long estCompressSum(byte[] dataRawRowSub) 
    {
        long sum = 0;
        for (byte aDataRawRowSub : dataRawRowSub)
            if (aDataRawRowSub > 0)
                sum += aDataRawRowSub;
            else
                sum -= aDataRawRowSub;
        return sum;
    }

}
