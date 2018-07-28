package org.apache.pdfbox.pdmodel.graphics.image;

import org.apache.pdfbox.pdmodel.PDDocument;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Load govdocs and use them to test the encoder. It will fetch the govdocs from the net. You can also pre-download them
 * to speed up repeated tests. Just give the directory where you downloaded the zip-files as first command line argument
 * 
 * @author Tilman Hausherr
 */
public class LoadGovdocs
{
    static Set<String> suffixes = new HashSet<String>(
            Arrays.asList(ImageIO.getReaderFileSuffixes()));
    static File outputDir = new File("/tmp/loadgovdocs_out");

    static class CompressInfoEntry
    {
        String filename;
        int sizeSigned;
        int sizeAbs;
    }

    static Vector<CompressInfoEntry> infoEntries = new Vector<CompressInfoEntry>();

    public static void main(String[] args) throws IOException
    {
        System.out.println("supported suffixes: " + suffixes);
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
        outputDir.mkdirs();
        if (args.length > 0)
        {
            String directory = args[0];
            FileWriter fileWriter = new FileWriter(new File(outputDir, "size_compare.txt"));
            File[] files = new File(directory).listFiles();
            if (files != null)
            {
                for (File file : files)
                {
                    if (file.getName().endsWith(".zip"))
                    {
                        FileInputStream inputStream = new FileInputStream(file);
                        try
                        {
                            System.out.println("Processing " + file.getName());
                            processZipStream(inputStream);
                            for (CompressInfoEntry infoEntry : infoEntries)
                            {
                                fileWriter.append(String.format("%s %8d (sign) / %8d (abs) %s\n",
                                        infoEntry.filename, infoEntry.sizeSigned, infoEntry.sizeAbs,
                                        infoEntry.sizeAbs < infoEntry.sizeSigned ? " abs better"
                                                : infoEntry.sizeSigned < infoEntry.sizeAbs
                                                        ? " signed better " : " both equal "));
                            }
                            infoEntries.clear();
                            fileWriter.flush();
                        }
                        finally
                        {
                            inputStream.close();
                        }
                    }
                }
            }
            fileWriter.close();
        }
        else
        {
            for (int zipNum = 0; zipNum <= 1000; ++zipNum)
            {
                String urlStr = String.format(
                        "http://downloads.digitalcorpora.org/corpora/files/govdocs1/zipfiles/%03d.zip",
                        zipNum);
                String zipName = urlStr.substring(urlStr.lastIndexOf('/') + 1);
                processZipURL(urlStr, zipName);
                new File(zipName).delete();
            }
        }
    }

    private static void loadZip(String urlStr, String zipName) throws IOException
    {
        System.out.println(urlStr);
        URL url = new URL(urlStr);
        InputStream is = url.openStream();
        FileOutputStream output = new FileOutputStream(zipName);
        try
        {
            int len;
            byte[] buffer = new byte[1024];
            while ((len = is.read(buffer)) > 0)
            {
                output.write(buffer, 0, len);
            }
        }
        finally
        {
            output.close();
        }
    }

    private static void processZipURL(String urlStr, String zipName) throws IOException
    {
        // loadZip(urlStr, zipName);
        //
        // InputStream is = new FileInputStream(zipName);

        InputStream is = new URL(urlStr).openStream();
        try
        {
            processZipStream(is);
        }
        finally
        {
            is.close();
        }
    }

    private static void processZipStream(InputStream is) throws IOException
    {
        try
        {
            ZipInputStream zip = new ZipInputStream(new BufferedInputStream(is));
            try
            {
                ZipEntry ze;
                while ((ze = zip.getNextEntry()) != null)
                {
                    if (ze.isDirectory())
                    {
                        continue;
                    }
                    String suffix = ze.getName().replaceFirst(".*\\.", ""); // works with a.b.c.d.png because regexp ist greedy!
                    if (suffixes.contains(suffix))
                    {
                        BufferedImage bim1;
                        String fileName = ze.getName();
                        fileName = fileName.substring(fileName.lastIndexOf('/') + 1);

                        CompressInfoEntry infoEntry = new CompressInfoEntry();
                        infoEntry.filename = fileName;
                        byte[] originalImageBytes;
                        try
                        {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            try
                            {
                                int len;
                                byte[] buffer = new byte[1024];
                                while ((len = zip.read(buffer)) > 0)
                                {
                                    baos.write(buffer, 0, len);
                                }
                                originalImageBytes = baos.toByteArray();
                                bim1 = ImageIO.read(new ByteArrayInputStream(originalImageBytes));
                            }
                            finally
                            {
                                baos.close();
                            }
                        }
                        catch (Throwable ex)
                        {
                            System.err.println(ze.getName() + " bad, skipped");
                            // ex.printStackTrace();
                            continue;
                        }

                        // now create PDFBox image and compare
                        PDDocument doc = new PDDocument();
                        try
                        {
                            // create
                            LosslessFactory.useAbsEstSum = false;
                            PDImageXObject imgXObject = LosslessFactory.createFromImage(doc, bim1);
                            LosslessFactory.useAbsEstSum = true;
                            PDImageXObject imgXObjectAbs = LosslessFactory.createFromImage(doc,
                                    bim1);
                            System.out.println(imgXObject.getCOSObject());
                            if (imgXObject.getBitsPerComponent() != 8)
                            {
                                System.out.println("bpc: " + imgXObject.getBitsPerComponent());
                            }
                            BufferedImage bim2 = imgXObject.getImage();

                            // compare
                            boolean good = isEqual(ze, bim1, bim2);

                            if (!good)
                            {
                                LosslessFactory.usePredictorEncoder = false;
                                imgXObject = LosslessFactory.createFromImage(doc, bim1);
                                boolean isOldEncoderEqual = isEqual(ze, bim1,
                                        imgXObject.getImage());
                                if (!isOldEncoderEqual)
                                {
                                    // Old encoder also had a color mismatch
                                    good = true;
                                }
                                LosslessFactory.usePredictorEncoder = true;
                            }
                            if (!good)
                            {
                                System.err.println(ze.getName() + ": images not equal");

                                FileOutputStream outputStream = new FileOutputStream(
                                        new File(outputDir, "org-" + fileName));
                                try
                                {
                                    outputStream.write(originalImageBytes);
                                }
                                finally
                                {
                                    outputStream.close();
                                }
                                ImageIO.write(bim1, "png",
                                        new File(outputDir, "src-" + fileName + ".png"));
                                ImageIO.write(bim1, "png",
                                        new File(outputDir, "dst-" + fileName + ".png"));
                                System.err.println(ze.getName() + " error");
                            }
                            else
                            {
                                infoEntry.sizeAbs = imgXObjectAbs.getStream().getLength();
                                infoEntry.sizeSigned = imgXObject.getStream().getLength();
                                infoEntries.add(infoEntry);
                                System.out.println(ze.getName() + " ok");
                            }
                        }
                        finally
                        {
                            doc.close();
                        }
                    }
                }
            }
            finally
            {
                zip.close();
            }
        }
        catch (EOFException ex)
        {
            // EOF, maybe network error. Skip.
            ex.printStackTrace();
        }
    }

    private static boolean isEqual(ZipEntry ze, BufferedImage bim1, BufferedImage bim2)
    {
        if (bim1.getWidth() != bim2.getWidth() || bim1.getHeight() != bim2.getHeight())
        {
            System.err.println(ze.getName() + ": sizes not equal");
        }
        boolean good = true;
        for (int y = 0; y < bim1.getHeight() && good; ++y)
        {
            for (int x = 0; x < bim1.getWidth(); ++x)
            {
                int rgb1 = bim1.getRGB(x, y);
                int rgb2 = bim2.getRGB(x, y);
                if (rgb1 != rgb2
                        // don't bother about small differences
                        && (Math.abs((rgb1 & 0xFF) - (rgb2 & 0xFF)) > 1
                                || Math.abs(((rgb1 >> 8) & 0xFF) - ((rgb2 >> 8) & 0xFF)) > 1
                                || Math.abs(((rgb1 >> 16) & 0xFF) - ((rgb2 >> 16) & 0xFF)) > 1))
                {
                    System.err.println(ze.getName() + ": "
                            + String.format("(%d,%d) %08X != %08X", x, y, rgb1, rgb2));
                    good = false;
                    break;
                }
            }
        }
        return good;
    }

}
