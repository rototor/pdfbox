/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.cos.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.graphics.color.PDColorSpace;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceGray;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.color.PDICCBased;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * This factory tries to encode a PNG given as byte array into a PDImageXObject
 * by directly coping the image data into the PDF streams without
 * decoding/encoding and re-compressing the PNG data.
 * 
 * If this is for any reason not possible, the factory will return null. You
 * must then encode the image by loading it and using the LosslessFactory.
 *
 * The W3C PNG spec was used to implement this class:
 * https://www.w3.org/TR/2003/REC-PNG-20031110
 * 
 * @author Emmeran Seehuber
 */
final class PNGConverter
{
	private static final Log LOG = LogFactory.getLog(PNGConverter.class);
	private PNGConverter()
	{
	}

	/**
	 * Try to convert a PNG into a PDImageXObject. If for any reason the PNG can not
	 * be converted, null is returned.
	 * 
	 * This usually means the PNG structure is damaged (CRC error, etc.) or it uses
	 * some features which can not be mapped to PDF.
	 * 
	 * @param doc
	 *            the document to put the image in
	 * @param imageData
	 *            the byte data of the PNG
	 * @return null or the PDImageXObject built from the png
	 */
	static PDImageXObject convertPNGImage(PDDocument doc, byte[] imageData) throws IOException
	{
		PNGConverterState state = parsePNGChunks(imageData);
		if (!checkConverterState(state))
		{
			// There is something wrong, we can't convert this PNG
			return null;
		}
		
		return convertPng(doc, state);
	}

	/**
	 * Convert the image using the state.
	 * 
	 * @param doc
	 *            the document to put the image in
	 * @param state
	 *            the parser state containing the PNG chunks.
	 * @return null or the converted image
	 */
	private static PDImageXObject convertPng(PDDocument doc, PNGConverterState state)
			throws IOException
	{
		Chunk ihdr = state.IHDR;
		int ihdrStart = ihdr.start;
		int width = readInt(ihdr.bytes, ihdrStart);
		int height = readInt(ihdr.bytes, ihdrStart + 4);
		int bitDepth = ihdr.bytes[ihdrStart + 8] & 0xFF;
		int colorType = ihdr.bytes[ihdrStart + 9] & 0xFF;
		int compressionMethod = ihdr.bytes[ihdrStart + 10] & 0xFF;
		int filterMethod = ihdr.bytes[ihdrStart + 11] & 0xFF;
		int interlaceMethod = ihdr.bytes[ihdrStart + 12] & 0xFF;
		
		if (bitDepth != 1 && bitDepth != 2 && bitDepth != 4 && bitDepth != 8 && bitDepth != 16 )
		{
			LOG.error(String.format("Invalid bit depth %d.", bitDepth));
			return null;
		}
		if (width <= 0 || height <= 0) 
		{
			LOG.error(String.format("Invalid image size %d x %d", width, height));
			return null;
		}
		if (compressionMethod != 0)
		{
			LOG.error(String.format("Unknown PNG compression method %d.", compressionMethod));
			return null;
		}
		if (filterMethod != 0)
		{
			LOG.error(String.format("Unknown PNG filtering method %d.", compressionMethod));
			return null;
		}
		if (interlaceMethod != 0)
		{
			LOG.debug(String.format("Can't handle interlace method %d.", interlaceMethod));
			return null;
		}
		
		state.width = width;
		state.height = height;
		state.bitsPerComponent = bitDepth;

		switch(colorType)
		{
		case 0:
			// Grayscale
			return buildImageObject(doc, PDDeviceGray.INSTANCE, state);
		case 2:
		    // Truecolor
			return buildImageObject(doc, PDDeviceRGB.INSTANCE, state);
		case 3:
			// Indexed image
			return buildIndexImage(doc, state);
		case 4:
			// Grayscale with alpha.
			LOG.debug("Can't handle grayscale with alpha, would need to separate alpha from image data");
            return null;
		case 6:
			// Truecolor with alpha.
			LOG.debug("Can't handle truecolor with alpha, would need to separate alpha from image data");
			return null;
		default:
			LOG.error("Unknown PNG color type " + colorType);
			return null;
		}
	}

	/**
	 * Build a indexed image
	 */
	private static PDImageXObject buildIndexImage(PDDocument doc, PNGConverterState state)
			throws IOException
	{
		if (state.PLTE == null)
		{
			LOG.error("Indexed image without PLTE chunk.");
			return null;
		}
		if (state.bitsPerComponent > 8)
		{
			LOG.debug(String.format("Can only convert indexed images with bit depth <= 8, not %d.",
					state.bitsPerComponent));
			return null;
		}
		
		PDImageXObject image = buildImageObject(doc, PDDeviceRGB.INSTANCE, state);
		if (image == null)
		{
			return null;
		}

		// Handle transparency
		if (state.tRNS != null)
		{
			// Yes, we need to duplicate the COSStream  here, don't know how to share
			// that between streams
			PDImageXObject smask = buildImageObject(doc, PDDeviceGray.INSTANCE, state);
			image.getCOSObject().setItem(COSName.SMASK, smask);
		}
		
		return image;
	}

	/**
	 * Build the base image object from the IDATs and profile information
	 */
	private static PDImageXObject buildImageObject(PDDocument document,  PDColorSpace colorSpace, 
			PNGConverterState state) throws IOException
	{
		InputStream encodedByteStream;
		if (state.IDATs.size() == 1) 
		{
			// Common case, we just can use the byte array
			Chunk idat = state.IDATs.get(0);
			encodedByteStream = new ByteArrayInputStream(idat.bytes, idat.start, idat.length);
		}
		else 
		{
			// Special case, we must concat the IDATs first
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			for(Chunk idat : state.IDATs)
			{
				baos.write(idat.bytes, idat.start, idat.length);
			}
			encodedByteStream = new ByteArrayInputStream(baos.toByteArray());
		}
		
		PDImageXObject imageXObject = new PDImageXObject(document, encodedByteStream,
				COSName.FLATE_DECODE, state.width,
				state.height, state.bitsPerComponent, colorSpace);
		
		if (state.sRGB != null)
		{
		    if(state.sRGB.length != 1)
			{
				LOG.error(String.format("sRGB chunk has an invalid length of %d", state.sRGB.length));
				return null;
			}

			// Store the specified rendering intent
			imageXObject.getCOSObject().setItem(COSName.INTENT, COSInteger.get(state.sRGB.bytes[state.sRGB.start]));
		}
		
		COSDictionary decodeParms = new COSDictionary();
		decodeParms.setItem(COSName.BITS_PER_COMPONENT, COSInteger.get(state.bitsPerComponent));
		decodeParms.setItem(COSName.PREDICTOR, COSInteger.get(15));
		decodeParms.setItem(COSName.COLUMNS, COSInteger.get(state.width));
		decodeParms.setItem(COSName.COLORS, COSInteger.get(colorSpace.getNumberOfComponents()));
		imageXObject.getCOSObject().setItem(COSName.DECODE_PARMS, decodeParms);	
		
		if (state.iCCP != null) 
		{
		    // We have got a color profile, which we must attach
			PDICCBased profile = new PDICCBased(document);
			COSStream cosStream = profile.getPDStream().getCOSObject();
			OutputStream rawOutputStream = cosStream
					.createRawOutputStream();
			cosStream.setInt(COSName.N, colorSpace.getNumberOfComponents());
			cosStream.setItem(COSName.ALTERNATE,
					colorSpace.getNumberOfComponents() == 1 ? COSName.DEVICEGRAY : COSName.DEVICERGB);
			try
			{
				rawOutputStream.write(state.iCCP.bytes, state.iCCP.start, state.iCCP.length);
			}
			finally 
			{
				rawOutputStream.close();
			}
			
			imageXObject.setColorSpace(profile);
		}
		return imageXObject;
	}

	/**
	 * Check if the converter state is sane.
	 * 
	 * @param state
	 *            the parsed converter state
	 * @return true if the state seems plausible
	 */
	static boolean checkConverterState(PNGConverterState state) 
	{
		if (state == null)
		{
			return false;
		}
		if (state.IHDR == null || !checkChunkSane(state.IHDR))
		{
			LOG.error("Invalid IHDR chunk.");
			return false;
		}
		if (!checkChunkSane(state.PLTE))
		{
			LOG.error("Invalid PLTE chunk.");
			return false;
		}
		if (!checkChunkSane(state.iCCP))
		{
			LOG.error("Invalid iCCP chunk.");
			return false;
		}
		if (!checkChunkSane(state.tRNS))
		{
			LOG.error("Invalid tRNS chunk.");
			return false;
		}
		if (!checkChunkSane(state.sRGB))
		{
			LOG.error("Invalid sRGB chunk.");
			return false;
		}
		
		if ((state.hadGama | state.hadChroma) && state.sRGB == null && state.iCCP == null)
		{
			LOG.debug("We only have gama and chroma curves, but no sRGB or ICC info. Can't convert.");
			return false;
		}
		
		// Check the IDATs
		if (state.IDATs.size() == 0)
		{
			LOG.error("No IDAT chunks.");
			return false;
		}
		for (Chunk idat : state.IDATs)
		{
			if(!checkChunkSane(idat))
			{
				LOG.error("Invalid IDAT chunk.");
				return false;
			}
		}
		return true;
	}

	/**
	 * Check if the chunk is sane, i.e. CRC matches and offsets and lengths in the
	 * byte array
	 */
	static boolean checkChunkSane(Chunk chunk)
	{
		if (chunk == null)
		{
			// If the chunk does not exist, it can not be wrong...
			return true;
		}

		if (chunk.start + chunk.length >= chunk.bytes.length)
		{
			return false;
		}

		if (chunk.start < 4)
		{
			return false;
		}
		
		// We must include the chunk type in the CRC calculation
		int ourCRC = crc(chunk.bytes, chunk.start - 4, chunk.length + 4);
		if(ourCRC != chunk.crc)
		{
			LOG.error(String.format("Invalid CRC %08X on chunk %08X, expected %08X.", ourCRC, chunk.chunkType,
					chunk.crc));
			return false;
		}
		return true;
	}



	/**
	 * Holds the information about a chunks
	 */
	static final class Chunk
	{
		byte[] bytes;
		int chunkType;
		int crc;
		int start;
		int length;
	}

	/**
	 * Holds all relevant chunks of the PNG
	 */
	static final class PNGConverterState
	{
		List<Chunk> IDATs = new ArrayList<Chunk>();
		Chunk IHDR;
		Chunk PLTE;
		Chunk iCCP;
		Chunk tRNS;
		Chunk sRGB;
		boolean hadGama;
		boolean hadChroma;
		
		// Parsed header fields
		int width; 
		int height; 
		int bitsPerComponent;
	}

	private static int readInt(byte[] data, int offset) {
		int b1 = (data[offset] & 0xFF) << 24;
		int b2 = (data[offset + 1] & 0xFF) << 16;
		int b3 = (data[offset + 2] & 0xFF) << 8;
		int b4 = (data[offset + 3] & 0xFF);
		return b1 | b2 | b3 | b4;
	}

	// Chunk Type definitions. The bytes in the comments are the bytes in the spec.
	private static final int CHUNK_IHDR = 0x49484452; // IHDR: 73 72 68 82
	private static final int CHUNK_IDAT = 0x49444154; // IDAT: 73 68 65 84
	private static final int CHUNK_PLTE = 0x504C5445; // PLTE: 80 76 84 69
	private static final int CHUNK_IEND = 0x49454E44; // IEND: 73 69 78 68
	private static final int CHUNK_tRNS = 0x74524E53; // tRNS: 116 82 78 83
	private static final int CHUNK_cHRM = 0x6348524D; // cHRM: 99 72 82 77
	private static final int CHUNK_gAMA = 0x67414D41; // gAMA: 103 65 77 65
	private static final int CHUNK_iCCP = 0x69434350; // iCCP: 105 67 67 80
	private static final int CHUNK_sBIT = 0x73424954; // sBIT: 115 66 73 84
	private static final int CHUNK_sRGB = 0x73524742; // sRGB: 115 82 71 66
	private static final int CHUNK_tEXt = 0x74455874; // tEXt: 116 69 88 116
	private static final int CHUNK_zTXt = 0x7A545874; // zTXt: 122 84 88 116
	private static final int CHUNK_iTXt = 0x69545874; // iTXt: 105 84 88 116
	private static final int CHUNK_kBKG = 0x6B424B47; // kBKG: 107 66 75 71
	private static final int CHUNK_hIST = 0x68495354; // hIST: 104 73 83 84
	private static final int CHUNK_pHYs = 0x70485973; // pHYs: 112 72 89 115
	private static final int CHUNK_sPLT = 0x73504C54; // sPLT: 115 80 76 84
	private static final int CHUNK_tIME = 0x74494D45; // tIME: 116 73 77 69

	/**
	 * Parse the PNG structure into the PNGConverterState. If we can't handle
	 * something, this method will return null.
	 * 
	 * @param imageData
	 *            the byte array with the PNG data
	 * @return null or the converter state with all relevant chunks
	 */
	private static PNGConverterState parsePNGChunks(byte[] imageData) 
	{
		if (imageData.length < 20) 
		{
			LOG.error("ByteArray way to small: " + imageData.length);
			return null;
		}
		
		PNGConverterState state = new PNGConverterState();
		int ptr = 8;
		int firstChunkType = readInt(imageData, ptr + 4);
		
		if(firstChunkType != CHUNK_IHDR) 
		{
			LOG.error(String.format("First Chunktype was %08X, not IHDR", firstChunkType));
			return null;
		}
		
		while (ptr + 12 <= imageData.length)
		{
			int chunkLength = readInt(imageData, ptr);
			int chunkType = readInt(imageData, ptr + 4);
			ptr += 8;
			
			if (ptr + chunkLength + 4 > imageData.length) {
				LOG.error("Not enough bytes. At offset " + ptr + " are " + chunkLength
						+ " bytes expected. Overall length is " + imageData.length);
				return null;
			}

			Chunk chunk = new Chunk();
			chunk.chunkType = chunkType;
			chunk.bytes = imageData;
			chunk.start = ptr;
			chunk.length = chunkLength;

			switch (chunkType) {
			case CHUNK_IHDR:
				if (state.IHDR != null)
				{
					LOG.error("Two IHDR chunks? There is something wrong.");
					return null;
				}
				state.IHDR = chunk;
				break;
			case CHUNK_IDAT:
			    // The image data itself
				state.IDATs.add(chunk);
				break;
			case CHUNK_PLTE:
				// For indexed images the palette table
				if (state.PLTE != null)
				{
					LOG.error("Two PLTE chunks? There is something wrong.");
					return null;
				}
				state.PLTE = chunk;
				break;
			case CHUNK_IEND:
			    // We are done, return the state
				return state;
			case CHUNK_tRNS:
				// For indexed images the alpha transparency table
				if (state.tRNS != null)
				{
					LOG.error("Two tRNS chunks? There is something wrong.");
					return null;
				}
			    state.tRNS = chunk;
			    break;
			case CHUNK_gAMA:
				state.hadGama = true;
				break;
			case CHUNK_cHRM:
				state.hadChroma = true;
				break;
			case CHUNK_iCCP:
				state.iCCP = chunk;
				break;
			case CHUNK_sBIT:
				LOG.debug("Can't convert PNGs with sBIT chunk.");
				break;
			case CHUNK_sRGB:
				// We use the rendering intent from the chunk
				state.sRGB = chunk;
				break;
			case CHUNK_tEXt:
			case CHUNK_zTXt:
			case CHUNK_iTXt:
				// We don't care about this text infos / metadata
				break;
			case CHUNK_kBKG:
				// As we can handle transparency we don't need the background color information.
				break;
			case CHUNK_hIST:
				// We don't need the color histogram
			    break;
			case CHUNK_pHYs:
			    // The PDImageXObject will be placed by the user however he wants,
				// so we can not enforce the physical dpi information stored here.
				// We just ignore it.
				break;
			case CHUNK_sPLT:
				// This palette stuff seems editor related, we don't need it.
				break;
			case CHUNK_tIME:
				// We don't need the last image change time either
				break;
			default:
				LOG.debug(String.format("Unknown chunk type %08X, skipping.", chunkType));
				break;
			}
			ptr += chunkLength;

			// Read the CRC
			chunk.crc = readInt(imageData, ptr);
			ptr += 4;
		}
		LOG.error("No IEND chunk found.");
		return null;
	}




	// CRC  Reference Implementation, see 
	// https://www.w3.org/TR/2003/REC-PNG-20031110/#D-CRCAppendix 
	// for details
	
	/* Table of CRCs of all 8-bit messages. */
	private static final int[] CRC_TABLE = new int[256];
	static {
		makeCrcTable();
	}

	/* Make the table for a fast CRC. */
	private static void makeCrcTable()
	{
		int c;

		for (int n = 0; n < 256; n++)
		{
			c = n;
			for (int k = 0; k < 8; k++)
			{
				if ((c & 1) != 0)
				{
					c = 0xEDB88320 ^ (c >>> 1);
				}
				else
				{
					c = c >>> 1;
				}
			}
			CRC_TABLE[n] = c;
		}
	}

	 /* Update a running CRC with the bytes buf[0..len-1]--the CRC
      should be initialized to all 1's, and the transmitted value
      is the 1's complement of the final running CRC (see the
      crc() routine below). */
	private static int updateCrc(byte[] buf, int offset, int len) 
	{
		int c = -1;

		int end = offset + len;
		for (int n = offset; n < end; n++) 
		{
			c = CRC_TABLE[(c ^ buf[n]) & 0xff] ^ (c >>> 8);
		}
		return c;
	}

	/* Return the CRC of the bytes buf[offset..(offset+len-1)]. */
	static int crc(byte[] buf, int offset, int len) 
	{
		return ~updateCrc(buf, offset, len);
	}
}
