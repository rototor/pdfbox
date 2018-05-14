package org.apache.pdfbox.pdmodel.graphics.image;

import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 4)
@Threads(1)
public class LosslessFactoryBenchmark
{
    private BufferedImage imgSmall;
    private PDDocument doc;
    private BufferedImage imgBig;
    private BufferedImage imgBigBytes;

    @Setup
    public void setupBenchmark() throws IOException
    {
        System.setProperty("sun.java2d.cmm", "sun.java2d.cmm.kcms.KcmsServiceProvider");
        imgSmall = ImageIO.read(LosslessFactory.class.getResourceAsStream("png.png"));
        imgBig = new BufferedImage(imgSmall.getWidth() * 10, imgSmall.getHeight() * 10,
                BufferedImage.TYPE_INT_RGB);
        imgBigBytes = new BufferedImage(imgSmall.getWidth() * 10, imgSmall.getHeight() * 10,
                BufferedImage.TYPE_3BYTE_BGR);

        Graphics2D graphics = imgBig.createGraphics();
        graphics.drawImage(imgSmall, 0, 0, imgBig.getWidth(), imgBig.getWidth(), null);
        graphics.dispose();

        graphics = imgBigBytes.createGraphics();
        graphics.drawImage(imgSmall, 0, 0, imgBigBytes.getWidth(), imgBigBytes.getWidth(), null);
        graphics.dispose();

        doc = new PDDocument();
    }

    @SuppressWarnings("WeakerAccess")
    @Param({ "3", "6", "9" })
    public String zipLevel;

    @Benchmark()
    public PDImageXObject rgbOnly() throws IOException
    {
        System.setProperty(Filter.SYSPROP_DEFLATELEVEL, zipLevel);
        LosslessFactory.usePredictorEncoder = false;
        return LosslessFactory.createFromImage(doc, imgSmall);
    }

    @Benchmark
    public PDImageXObject predictor() throws IOException
    {
        System.setProperty(Filter.SYSPROP_DEFLATELEVEL, zipLevel);
        LosslessFactory.usePredictorEncoder = true;
        return LosslessFactory.createFromImage(doc, imgSmall);
    }

    @Benchmark()
    public PDImageXObject rgbOnlyBig() throws IOException
    {
        System.setProperty(Filter.SYSPROP_DEFLATELEVEL, zipLevel);
        LosslessFactory.usePredictorEncoder = false;
        return LosslessFactory.createFromImage(doc, imgBig);
    }

    @Benchmark
    public PDImageXObject predictorBig() throws IOException
    {
        System.setProperty(Filter.SYSPROP_DEFLATELEVEL, zipLevel);
        LosslessFactory.usePredictorEncoder = true;
        return LosslessFactory.createFromImage(doc, imgBig);
    }

    @Benchmark()
    public PDImageXObject rgbOnlyBigBytes() throws IOException
    {
        System.setProperty(Filter.SYSPROP_DEFLATELEVEL, zipLevel);
        LosslessFactory.usePredictorEncoder = false;
        return LosslessFactory.createFromImage(doc, imgBigBytes);
    }

    @Benchmark
    public PDImageXObject predictorBigBytes() throws IOException
    {
        System.setProperty(Filter.SYSPROP_DEFLATELEVEL, zipLevel);
        LosslessFactory.usePredictorEncoder = true;
        return LosslessFactory.createFromImage(doc, imgBigBytes);
    }

    private static boolean DO_PROFILE_LOOP = false;

    public static void main(String[] args) throws RunnerException, IOException
    {
        if (DO_PROFILE_LOOP)
        {
            LosslessFactoryBenchmark benchmark = new LosslessFactoryBenchmark();
            benchmark.setupBenchmark();
            benchmark.zipLevel = "3";
            PDImageXObject last = null;

            benchmark.zipLevel = "9";
            PDImageXObject tst = benchmark.predictorBig();
            PDImageXObject tst2 = benchmark.rgbOnlyBig();


           System.out.println(" " + tst.getStream().getLength() + " vs. " + tst2.getStream().getLength());

            for (int i = 0; i < 1000000; i++)
            {
                PDImageXObject now = benchmark.predictorBig();
                // Those lines are to keep the VM from optimizing the whole call out...
                if (last == now)
                    throw new IllegalStateException();
                last = now;
            }
        }

        Options opt = new OptionsBuilder()
                .include(".*" + LosslessFactoryBenchmark.class.getSimpleName() + ".*").forks(1)
                .build();

        new Runner(opt).run();
    }
}
