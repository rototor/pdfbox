package org.apache.pdfbox.pdmodel.graphics.image;

import static org.apache.pdfbox.pdmodel.graphics.image.ValidateXImage.checkIdent;
import static org.junit.Assert.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import javax.imageio.ImageIO;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.junit.Before;
import org.junit.Test;

public class PNGConverterTest
{

    /**
     * This "test" just dumps the list of constants for the PNGConverter CHUNK_??? types, so that
     * it can just be copy&pasted into the PNGConverter class.
     */
    @Test
    public void dumpChunkTypes()
    {
        final String[] chunkTypes = 
        {
                "IHDR",
                "IDAT",
                "PLTE",
                "IEND",
                "tRNS",
                "cHRM",
                "gAMA",
                "iCCP",
                "sBIT",
                "sRGB",
                "tEXt",
                "zTXt",
                "iTXt",
                "kBKG",
                "hIST",
                "pHYs",
                "sPLT",
                "tIME"
        };
        
		for (String chunkType : chunkTypes)
        {
            byte[] bytes = chunkType.getBytes();
			assertEquals(4, bytes.length);
            System.out.println(String.format(
					"\tprivate static final int CHUNK_" + chunkType + " = 0x%02X%02X%02X%02X; // %s: %d %d %d %d",
					(int) bytes[0] & 0xFF, (int) bytes[1] & 0xFF, (int) bytes[2] & 0xFF,
                    (int) bytes[3] & 0xFF,
                    chunkType,
                    (int) bytes[0] & 0xFF, (int) bytes[1] & 0xFF, (int) bytes[2] & 0xFF,
                    (int) bytes[3] & 0xFF
                    ));
        }
    }
    
    @Test
    public void testImageConversionRGB() throws IOException
    {
        checkImageConvert("png.png");
    }

    @Test
    public void testImageConversionRGBGamma() throws IOException
    {
        checkImageConvertFail("png_rgb_gamma.png");
    }

    @Test
    public void testImageConversionRGB16BitICC() throws IOException
    {
        checkImageConvert("png_rgb_romm_16bit.png");
    }

    @Test
    public void testImageConversionRGBIndexed() throws IOException
    {
        checkImageConvert("png_indexed.png");
    }

    @Test
    public void testImageConversionRGBIndexedAlpha() throws IOException
    {
        checkImageConvertFail("png_indexed_alpha.png");
    }

    @Test
    public void testImageConversionRGBAlpha() throws IOException
    {
        // We can't handle Alpha RGB
        checkImageConvertFail("png_alpha_rgb.png");
    }

    @Test
    public void testImageConversionGrayAlpha() throws IOException
    {
        // We can't handle Alpha RGB
        checkImageConvertFail("png_alpha_gray.png");
    }

    @Test
    public void testImageConversionGray() throws IOException
    {
        checkImageConvertFail("png_gray.png");
    }

    @Test
    public void testImageConversionGrayGamma() throws IOException
    {
        checkImageConvertFail("png_gray_with_gama.png");
    }

	private final File parentDir = new File("target/test/pngconvert");

    @Before 
	public void setup() 
    {
        //noinspection ResultOfMethodCallIgnored
        parentDir.mkdirs();
	}
    
    private void checkImageConvertFail(String name) throws IOException
    {
		PDDocument doc = new PDDocument();
		byte[] imageBytes = IOUtils.toByteArray(PNGConverterTest.class.getResourceAsStream(name));
		PDImageXObject pdImageXObject = PNGConverter.convertPNGImage(doc, imageBytes);
		assertNull(pdImageXObject);
		doc.close();
    }

    private void checkImageConvert(String name) throws IOException
    {
		PDDocument doc = new PDDocument();
		byte[] imageBytes = IOUtils.toByteArray(PNGConverterTest.class.getResourceAsStream(name));
		PDImageXObject pdImageXObject = PNGConverter.convertPNGImage(doc, imageBytes);
		assertNotNull(pdImageXObject);
        PDPage page = new PDPage();
        doc.addPage(page);
        PDPageContentStream contentStream = new PDPageContentStream(doc, page);
        contentStream.setNonStrokingColor(Color.PINK);
        contentStream.addRect(0, 0, page.getCropBox().getWidth(), page.getCropBox().getHeight());
        contentStream.fill();

        contentStream.drawImage(pdImageXObject, 0, 0, pdImageXObject.getWidth(), pdImageXObject.getHeight());
        contentStream.close();
        doc.save(new File(parentDir, name + ".pdf"));
        BufferedImage image = pdImageXObject.getImage();
        checkIdent(ImageIO.read(new ByteArrayInputStream(imageBytes)), image);
		doc.close();
    }

    @Test
    public void testCheckConverterState()
    {
        assertFalse(PNGConverter.checkConverterState(null));
        PNGConverter.PNGConverterState state = new PNGConverter.PNGConverterState();
        assertFalse(PNGConverter.checkConverterState(state));
        
        PNGConverter.Chunk invalidChunk = new PNGConverter.Chunk();
        invalidChunk.bytes = new byte[0];
        assertFalse(PNGConverter.checkChunkSane(invalidChunk));

        // Valid Dummy Chunk
        PNGConverter.Chunk validChunk = new PNGConverter.Chunk();
        validChunk.bytes = new byte[16];
        validChunk.start = 4;
        validChunk.length = 8;
        validChunk.crc = 2077607535;
        assertTrue(PNGConverter.checkChunkSane(validChunk));

        state.IHDR = invalidChunk;
        assertFalse(PNGConverter.checkConverterState(state));
        state.IDATs = Collections.singletonList(validChunk);
        assertFalse(PNGConverter.checkConverterState(state));
        state.IHDR = validChunk;
        assertTrue(PNGConverter.checkConverterState(state));
        state.IDATs = new ArrayList<PNGConverter.Chunk>();
        assertFalse(PNGConverter.checkConverterState(state));
        state.IDATs = Collections.singletonList(validChunk);
        assertTrue(PNGConverter.checkConverterState(state));

        state.PLTE = invalidChunk;
        assertFalse(PNGConverter.checkConverterState(state));
        state.PLTE = validChunk;
        assertTrue(PNGConverter.checkConverterState(state));

		state.cHRM = invalidChunk;
		assertFalse(PNGConverter.checkConverterState(state));
		state.cHRM = validChunk;
        assertTrue(PNGConverter.checkConverterState(state));
        
		state.tRNS = invalidChunk;
		assertFalse(PNGConverter.checkConverterState(state));
		state.tRNS = validChunk;
        assertTrue(PNGConverter.checkConverterState(state));

        state.iCCP = invalidChunk;
        assertFalse(PNGConverter.checkConverterState(state));
        state.iCCP = validChunk;
        assertTrue(PNGConverter.checkConverterState(state));
        
		state.sRGB = invalidChunk;
		assertFalse(PNGConverter.checkConverterState(state));
		state.sRGB = validChunk;
        assertTrue(PNGConverter.checkConverterState(state));
        
		state.gAMA = invalidChunk;
		assertFalse(PNGConverter.checkConverterState(state));
		state.gAMA = validChunk;
        assertTrue(PNGConverter.checkConverterState(state));

        state.IDATs = Arrays.asList(validChunk,invalidChunk);
        assertFalse(PNGConverter.checkConverterState(state));

    }

    @Test
    public void testChunkSane()
    {
        PNGConverter.Chunk chunk = new PNGConverter.Chunk();
        assertTrue(PNGConverter.checkChunkSane(null));
        chunk.bytes = "IHDRsomedummyvaluesDummyValuesAtEnd".getBytes();
        chunk.length = 19;
		assertEquals(chunk.bytes.length, 35);
		
		assertEquals("IHDRsomedummyvalues", new String(chunk.getData()));

        assertFalse(PNGConverter.checkChunkSane(chunk));
        chunk.start = 4;
        assertEquals("somedummyvaluesDumm", new String(chunk.getData()));
        assertFalse(PNGConverter.checkChunkSane(chunk));
        chunk.crc = -1729802258;
        assertTrue(PNGConverter.checkChunkSane(chunk));
        chunk.start = 6;
        assertFalse(PNGConverter.checkChunkSane(chunk));
        chunk.length = 60;
        assertFalse(PNGConverter.checkChunkSane(chunk));
    }
    
    @Test
    public void testCRCImpl()
    {
        byte[] b1 = "Hello World!".getBytes();
        assertEquals(472456355, PNGConverter.crc(b1,0,b1.length));
		assertEquals(-632335482, PNGConverter.crc(b1, 2, b1.length - 4));
    }

    @Test
    public void testMapPNGRenderIntent()
    {
		assertEquals(COSName.PERCEPTUAL, PNGConverter.mapPNGRenderIntent(0));
		assertEquals(COSName.RELATIVE_COLORIMETRIC, PNGConverter.mapPNGRenderIntent(1));
		assertEquals(COSName.SATURATION, PNGConverter.mapPNGRenderIntent(2));
		assertEquals(COSName.ABSOLUTE_COLORIMETRIC, PNGConverter.mapPNGRenderIntent(3));
		assertNull(PNGConverter.mapPNGRenderIntent(-1));
		assertNull(PNGConverter.mapPNGRenderIntent(4));

    }
}
