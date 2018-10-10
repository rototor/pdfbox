package org.apache.pdfbox.pdmodel.graphics.image;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.apache.pdfbox.pdmodel.graphics.image.ValidateXImage.checkIdent;
import static org.junit.Assert.*;

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
    public void testImageConversion() throws IOException
    {
        checkImageConvert("png.png");
        checkImageConvert("png_gray.png");
        checkImageConvert("png_gray_with_gama.png");
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
        
        PNGConverter.Chunk chunk = new PNGConverter.Chunk();
        chunk.bytes = new byte[0];
        assertFalse(PNGConverter.checkChunkSane(chunk));
        
        // TODO
    }

    @Test
    public void testChunkSane(){
        PNGConverter.Chunk chunk = new PNGConverter.Chunk();
        assertTrue(PNGConverter.checkChunkSane(null));
        chunk.bytes = "IHDRsomedummyvaluesDummyValuesAtEnd".getBytes();
        chunk.length = 19;
		assertEquals(chunk.bytes.length, 35);
        assertFalse(PNGConverter.checkChunkSane(chunk));
        chunk.start = 4;
        assertFalse(PNGConverter.checkChunkSane(chunk));
        chunk.crc = -1729802258;
        assertTrue(PNGConverter.checkChunkSane(chunk));
        chunk.start = 6;
        assertFalse(PNGConverter.checkChunkSane(chunk));
    }
    
    @Test
    public void testCRCImpl(){
        byte[] b1 = "Hello World!".getBytes();
        assertEquals(472456355, PNGConverter.crc(b1,0,b1.length));
		assertEquals(-632335482, PNGConverter.crc(b1, 2, b1.length - 4));
    }
}
