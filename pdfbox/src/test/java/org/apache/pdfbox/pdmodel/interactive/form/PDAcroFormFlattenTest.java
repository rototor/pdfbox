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
package org.apache.pdfbox.pdmodel.interactive.form;

import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.TestPDFToImage;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;

/**
 * Test flatten different forms and compare with rendering.
 *
 * Some of the tests are currently disabled to not run within the CI environment
 * as the test results need manual inspection. Enable as needed.
 *
 */
public class PDAcroFormFlattenTest
{

    private static final File IN_DIR = new File("target/test-output/flatten/in");
    private static final File OUT_DIR = new File("target/test-output/flatten/out");

    @Before
    public void setUp()
    {
        IN_DIR.mkdirs();
        OUT_DIR.mkdirs();
    }

    /*
     * PDFBOX-142 Filled template.
     */
    // @Test
    public void testFlattenPDFBOX142() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12742551/Testformular1.pdf";
        String targetFileName = "Testformular1.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-563 Filled template.
     */
    // @Test
    public void testFlattenPDFBOX563() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12425859/TestFax_56972.pdf";
        String targetFileName = "TestFax_56972.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-2469 Empty template.
     */
    @Test
    public void testFlattenPDFBOX2469Empty() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12682897/FormI-9-English.pdf";
        String targetFileName = "FormI-9-English.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-2469 Filled template.
     */
    // @Test
    public void testFlattenPDFBOX2469Filled() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12678455/testPDF_acroForm.pdf";
        String targetFileName = "testPDF_acroForm.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-2586 Empty template.
     */
    @Test
    public void testFlattenPDFBOX2586() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12689788/test.pdf";
        String targetFileName = "test-2586.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3083 Filled template rotated.
     */
    // @Test
    public void testFlattenPDFBOX3083() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12770263/mypdf.pdf";
        String targetFileName = "mypdf.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3262 Hidden fields
     */
    @Test
    public void testFlattenPDFBOX3262() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12792007/hidden_fields.pdf";
        String targetFileName = "hidden_fields.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3396 Signed Document 1.
     */
    @Test
    public void testFlattenPDFBOX3396_1() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12816014/Signed-Document-1.pdf";
        String targetFileName = "Signed-Document-1.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3396 Signed Document 2.
     */
    @Test
    public void testFlattenPDFBOX3396_2() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12816016/Signed-Document-2.pdf";
        String targetFileName = "Signed-Document-2.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3396 Signed Document 3.
     */
    @Test
    public void testFlattenPDFBOX3396_3() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12821307/Signed-Document-3.pdf";
        String targetFileName = "Signed-Document-3.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3396 Signed Document 4.
     */
    @Test
    public void testFlattenPDFBOX3396_4() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12821308/Signed-Document-4.pdf";
        String targetFileName = "Signed-Document-4.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3587 Empty template.
     */
    // @Test
    public void testFlattenOpenOfficeForm() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12839977/OpenOfficeForm.pdf";
        String targetFileName = "OpenOfficeForm.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * PDFBOX-3587 Filled template.
     */
    // @Test
    public void testFlattenOpenOfficeFormFilled() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12840280/OpenOfficeForm_filled.pdf";
        String targetFileName = "OpenOfficeForm_filled.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /**
     * PDFBOX-4157 Filled template.
     */
    // @Test
    public void testFlattenPDFBox4157() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12976553/PDFBOX-4157-filled.pdf";
        String targetFileName = "PDFBOX-4157-filled.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /**
     * PDFBOX-4172 Filled template.
     */
    // @Test
    public void testFlattenPDFBox4172() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12976552/PDFBOX-4172-filled.pdf";
        String targetFileName = "PDFBOX-4172-filled.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /**
     * PDFBOX-4615 Filled template.
     */
    // @Test
    public void testFlattenPDFBox4615() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12976452/resetboundingbox-filled.pdf";
        String targetFileName = "PDFBOX-4615-filled.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /**
     * PDFBOX-4693: page is not rotated, but the appearance stream is.
     */
    @Test
    public void testFlattenPDFBox4693() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12986337/stenotypeTest-3_rotate_no_flatten.pdf";
        String targetFileName = "PDFBOX-4693-filled.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /**
     * PDFBOX-4788: non-widget annotations are not to be removed on a page that has no widget
     * annotations.
     */
    @Test
    public void testFlattenPDFBox4788() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/12994791/flatten.pdf";
        String targetFileName = "PDFBOX-4788.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /**
     * PDFBOX-4889: appearance streams with empty /BBox.
     * 
     * @throws IOException 
     */
    @Test
    public void testFlattenPDFBox4889() throws IOException
    {
        String sourceUrl = "https://issues.apache.org/jira/secure/attachment/13005793/f1040sb%20test.pdf";
        String targetFileName = "PDFBOX-4889.pdf";

        flattenAndCompare(sourceUrl, targetFileName);
    }

    /*
     * Flatten and compare with generated image samples.
     */
    private static void flattenAndCompare(String sourceUrl, String targetFileName) throws IOException
    {
        generateSamples(sourceUrl,targetFileName);

        File inputFile = new File(IN_DIR, targetFileName);
        File outputFile = new File(OUT_DIR, targetFileName);

        PDDocument testPdf = PDDocument.load(inputFile);
        testPdf.getDocumentCatalog().getAcroForm().flatten();
        testPdf.setAllSecurityToBeRemoved(true);
        assertTrue(testPdf.getDocumentCatalog().getAcroForm().getFields().isEmpty());
        testPdf.save(outputFile);
        testPdf.close();

        // compare rendering
        TestPDFToImage testPDFToImage = new TestPDFToImage(TestPDFToImage.class.getName());
        if (!testPDFToImage.doTestFile(outputFile, IN_DIR.getAbsolutePath(), OUT_DIR.getAbsolutePath()))
        {
            fail("Rendering of " + outputFile + " failed or is not identical to expected rendering in " + IN_DIR + " directory");
        }
        else
        {
            // cleanup input and output directory for matching files.
            removeAllRenditions(inputFile);
            inputFile.delete();
            outputFile.delete();
        }
    }

    /*
     * Generate the sample images to which the PDF will be compared after flatten.
     */
    private static void generateSamples(String sourceUrl, String targetFile) throws IOException
    {
        getFromUrl(sourceUrl, targetFile);

        File file = new File(IN_DIR,targetFile);

        PDDocument document = PDDocument.load(file, (String) null);
        String outputPrefix = IN_DIR.getAbsolutePath() + '/' + file.getName() + "-";
        int numPages = document.getNumberOfPages();

        PDFRenderer renderer = new PDFRenderer(document);
        for (int i = 0; i < numPages; i++)
        {
            String fileName = outputPrefix + (i + 1) + ".png";
            BufferedImage image = renderer.renderImageWithDPI(i, 96); // Windows native DPI
            ImageIO.write(image, "PNG", new File(fileName));
        }
        document.close();
    }

    /*
     * Get a PDF from URL and copy to file for processing.
     */
    private static void getFromUrl(String sourceUrl, String targetFile) throws IOException
    {
        URL url = new URL(sourceUrl);

        InputStream is = url.openStream();
        OutputStream os = new FileOutputStream(new File(IN_DIR, targetFile));

        byte[] b = new byte[2048];
        int length;

        while ((length = is.read(b)) != -1)
        {
            os.write(b, 0, length);
        }
        is.close();
        os.close();
    }

    /*
     * Remove renditions for the PDF from the input directory.
     * The output directory will have been cleaned by the TestPDFToImage utility.
     */
    private static void removeAllRenditions(final File inputFile)
    {
        File[] testFiles = inputFile.getParentFile().listFiles(new FilenameFilter()
        {
            @Override
            public boolean accept(File dir, String name)
            {
                return (name.startsWith(inputFile.getName()) && name.toLowerCase().endsWith(".png"));
            }
        });

        for (File testFile : testFiles)
        {
            testFile.delete();
        }
    }
}
