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
import java.awt.image.DataBuffer;
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
            // We try to encode the image with predictor 
			PDImageXObject pdImageXObject = compressImageWithPredictor(document, image);
			if (pdImageXObject != null)
            {
                return pdImageXObject;
            }

			// Fallback: We export the image as 8-bit sRGB and might loose color information
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
		ByteArrayOutputStream baos = new ByteArrayOutputStream(byteArray.length / 2);

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
	private static PDImageXObject compressImageWithPredictor(PDDocument document, 
            BufferedImage image) throws IOException {
        // The raw count of components per pixel including optional alpha
        final int componentsPerPixel = image.getColorModel().getNumComponents();
        final int transferType = image.getRaster().getTransferType();
		final int bytesPerComponent = (transferType == DataBuffer.TYPE_SHORT
				|| transferType == DataBuffer.TYPE_USHORT) ? 2 : 1;
        // Only the bytes we need in the output (excluding alpha)
		final int bytesPerPixel = image.getColorModel().getNumColorComponents() * bytesPerComponent;

        final int height = image.getHeight();
        final int width = image.getWidth();

        Raster imageRaster = image.getRaster();
        final int elementsInRowPerPixel;
        
        // This variable store a row of the image each, the exact type depends 
        // on the image encoding. Can be a int[], short[] or byte[]
        Object prevRow, transferRow;

        final int imageType = image.getType();
		final boolean hasAlpha = image.getColorModel().getNumComponents() != image.getColorModel()
				.getNumColorComponents();
		final byte[] alphaImageData = hasAlpha ? new byte[width * height] : null;
		
        switch (imageType) {
        case BufferedImage.TYPE_CUSTOM: {
            switch (imageRaster.getTransferType()) 
            {
            case DataBuffer.TYPE_USHORT:
                elementsInRowPerPixel = componentsPerPixel;
				prevRow = new short[width * elementsInRowPerPixel];
				transferRow = new short[width * elementsInRowPerPixel];
                break;
            case DataBuffer.TYPE_BYTE:
                elementsInRowPerPixel = componentsPerPixel;
                prevRow = new byte[width * elementsInRowPerPixel];
                transferRow = new byte[width * elementsInRowPerPixel];
                break;
            default:
                return null;
            }
            break;
        }
        
        case BufferedImage.TYPE_3BYTE_BGR:
        case BufferedImage.TYPE_4BYTE_ABGR:
        {
            elementsInRowPerPixel = componentsPerPixel;
            prevRow = new byte[width * elementsInRowPerPixel];
            transferRow = new byte[width * elementsInRowPerPixel];
            break;
        }
            
        case BufferedImage.TYPE_INT_BGR:
        case BufferedImage.TYPE_INT_ARGB:
        case BufferedImage.TYPE_INT_RGB:
        {
            elementsInRowPerPixel = 1;
            prevRow = new int[width * elementsInRowPerPixel];
            transferRow = new int[width * elementsInRowPerPixel];
            break;
        }
            
        default:
            // We can not handle this unknown format
            return null;
        }
        
        final int elementsInTransferRow = width * elementsInRowPerPixel;
        
        // The rows have 1-byte encoding marker and width*BYTES_PER_PIXEL pixel-bytes
        final int dataRowByteCount = width * bytesPerPixel + 1;
        final byte[] dataRawRowNone = new byte[dataRowByteCount];
        final byte[] dataRawRowSub = new byte[dataRowByteCount];
        final byte[] dataRawRowUp = new byte[dataRowByteCount];
        final byte[] dataRawRowAverage = new byte[dataRowByteCount];
        final byte[] dataRawRowPaeth = new byte[dataRowByteCount];

        // Write the encoding markers
        dataRawRowNone[0] = 0;
        dataRawRowSub[0] = 1;
        dataRawRowUp[0] = 2;
        dataRawRowAverage[0] = 3;
        dataRawRowPaeth[0] = 4;
        
		
        // pre-size the output stream to half of the maximum size
		ByteArrayOutputStream stream = new ByteArrayOutputStream(height * width * bytesPerPixel / 2);
        Deflater deflater = new Deflater(Filter.getCompressionLevel());
        DeflaterOutputStream zip = new DeflaterOutputStream(stream, deflater);

        int alphaPtr = 0;
        for (int i = 0; i < height; i++) 
        {
            imageRaster.getDataElements(0, i, width, 1, transferRow);

            // We start to write at index one, as the predictor marker is in index zero 
            int writerPtr = 1;
            byte[] aValues = new byte[bytesPerPixel];
            byte[] cValues = new byte[bytesPerPixel];
            byte[] bValues = new byte[bytesPerPixel];
            byte[] xValues = new byte[bytesPerPixel];
            byte[] tmpValues = new byte[bytesPerPixel];
            
			for (int j = 0; j < elementsInTransferRow; j += elementsInRowPerPixel, alphaPtr++) 
            {
				copyTransferRowIntoValues(prevRow, transferRow, imageType, alphaImageData, alphaPtr, 
                        bValues, xValues, j);

                writeEncodedValuesIntoRowBuffer(dataRawRowNone, dataRawRowSub, dataRawRowUp, 
                        dataRawRowAverage, dataRawRowPaeth, writerPtr, aValues, cValues, bValues, 
                        xValues, tmpValues, bytesPerPixel);

                /*
                 * We shift the values into the prev / upper left values for the next
                 * pixel
                 */
                System.arraycopy(xValues, 0, aValues, 0, bytesPerPixel);
                System.arraycopy(bValues, 0, cValues, 0, bytesPerPixel);

                writerPtr += bytesPerPixel;
            }

            byte[] rowToWrite = chooseDataRowToWrite(dataRawRowNone, dataRawRowSub, dataRawRowUp, 
                    dataRawRowAverage, dataRawRowPaeth);

            /*
             * Write and compress the row as long it is hot (CPU cache wise)
             */
            zip.write(rowToWrite, 0, rowToWrite.length);

            {
                /*
                 * We swap prev and transfer row, so that we have the prev row for the next 
                 * row.
                 */
                Object temp = prevRow;
                prevRow = transferRow;
                transferRow = temp;
            }
        }
        zip.close();
        deflater.end();

		return preparePredictorPDImage(document, image, stream, bytesPerComponent * 8, alphaImageData);
    }

    private static void copyTransferRowIntoValues(Object prevRow, Object transferRow, int imageType,
            byte[] alphaImageData, int alphaPtr, byte[] bValues, byte[] xValues, int indexInTransferRow)
    {
		if (transferRow instanceof byte[]) 
		{
            copyImageBytes((byte[]) transferRow, indexInTransferRow, xValues, alphaImageData, alphaPtr);
            copyImageBytes((byte[]) prevRow, indexInTransferRow, bValues, null, 0);
		}
		else if (transferRow instanceof int[]) 
		{
			copyIntToBytes((int[]) transferRow, indexInTransferRow, xValues, imageType, alphaImageData, alphaPtr);
			copyIntToBytes((int[]) prevRow, indexInTransferRow, bValues, imageType, null, 0);
		} 
		else if (transferRow instanceof short[]) 
		{
			copyShortsToBytes((short[]) transferRow, indexInTransferRow, xValues);
			copyShortsToBytes((short[]) prevRow, indexInTransferRow, bValues);
		}
    }

    private static void copyIntToBytes(int[] transferRow, int indexInTranferRow, byte[] targetValues, 
            int imageType, byte[] alphaImageData, int alphaPtr)
    {
        int val = transferRow[indexInTranferRow];
		byte b0 = (byte) ((val & 0xFF));
		byte b1 = (byte) ((val & 0xFF00) >> 8);
		byte b2 = (byte) ((val & 0xFF0000) >> 16);
		byte b3 = (byte) ((val & 0xFF000000) >> 24);

        switch(imageType){
        case BufferedImage.TYPE_INT_BGR: {
            targetValues[0] = b0;
            targetValues[1] = b1;
            targetValues[2] = b2;
            break;
        }
        case BufferedImage.TYPE_INT_ARGB:
        {
            targetValues[0] = b2;
            targetValues[1] = b1;
            targetValues[2] = b0;
            if (alphaImageData != null)
            {
                alphaImageData[alphaPtr] = b3;
            }
            break;
        }
        case BufferedImage.TYPE_INT_RGB:
            targetValues[0] = b2;
            targetValues[1] = b1;
            targetValues[2] = b0;
            break;
        }
    }

    private static void copyImageBytes(byte[] transferRow, int indexInTranferRow, byte[] targetValues,
            byte[] alphaImageData, int alphaPtr)
    {
        System.arraycopy(transferRow, indexInTranferRow, targetValues, 0, targetValues.length);
        if (alphaImageData != null)
        {
			alphaImageData[alphaPtr] = transferRow[indexInTranferRow + targetValues.length];
        }
    }

    private static void copyShortsToBytes(short[] transferRow, int indexInTranferRow, 
            byte[] targetValues) 
    {
		for (int i = 0; i < targetValues.length;) 
        {
			short val = transferRow[indexInTranferRow++];
			targetValues[i++] = (byte) ((val & 0xFF00) >> 8);
			targetValues[i++] = (byte) (val & 0xFF);
        }
    }

    private static PDImageXObject preparePredictorPDImage(PDDocument document, BufferedImage image,
            ByteArrayOutputStream stream, int bitsPerComponent, byte[] alphaImageData) throws IOException
    {
        int height = image.getHeight();
        int width = image.getWidth();

		ColorSpace srcCspace = image.getColorModel().getColorSpace();
		PDColorSpace pdColorSpace = srcCspace.getType() != ColorSpace.TYPE_CMYK ? PDDeviceRGB.INSTANCE
				: PDDeviceCMYK.INSTANCE;

        // Encode the image profile if the image has one
		if (srcCspace instanceof ICC_ColorSpace)
		{
            ICC_ColorSpace icc_colorSpace = (ICC_ColorSpace) srcCspace;
            ICC_Profile profile = icc_colorSpace.getProfile();
            // We only encode a color profile if it is not sRGB
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


        if (image.getTransparency() != Transparency.OPAQUE)
        {
            PDImageXObject pdMask = prepareImageXObject(document, alphaImageData,
                    image.getWidth(), image.getHeight(), 8, PDDeviceGray.INSTANCE);
            imageXObject.getCOSObject().setItem(COSName.SMASK, pdMask);
        }
		return imageXObject;
    }

    /**
     * Write the current pixel using different row encoding
     */
    private static void writeEncodedValuesIntoRowBuffer(byte[] dataRawRowNone, byte[] dataRawRowSub, byte[] dataRawRowUp, 
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
		{
			if (aDataRawRowSub > 0) 
			{
				sum += aDataRawRowSub;
			} 
			else
            {
				sum -= aDataRawRowSub;
			}
		}
		return sum;
    }

}
