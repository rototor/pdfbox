package org.apache.pdfbox.pdmodel.graphics.image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

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
		imgBig = new BufferedImage(imgSmall.getWidth() * 10, imgSmall.getHeight() * 10, BufferedImage.TYPE_INT_RGB);
		imgBigBytes = new BufferedImage(imgSmall.getWidth() * 10, imgSmall.getHeight() * 10, BufferedImage.TYPE_3BYTE_BGR);

		Graphics2D graphics = imgBig.createGraphics();
		graphics.drawImage(imgSmall, 0, 0, imgBig.getWidth(), imgBig.getWidth(), null);
		graphics.dispose();

		graphics = imgBigBytes.createGraphics();
		graphics.drawImage(imgSmall, 0, 0, imgBigBytes.getWidth(), imgBigBytes.getWidth(), null);
		graphics.dispose();

		doc = new PDDocument();
	}

	@Benchmark()
	public PDImageXObject rgbOnly9() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = false;
		return LosslessFactory.createFromImage(doc, imgSmall);
	}

	@Benchmark
	public PDImageXObject predictor9() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = true;
		return LosslessFactory.createFromImage(doc, imgSmall);
	}
	
	@Benchmark()
	public PDImageXObject rgbOnly6() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "6");
		LosslessFactory.usePredictorEncoder = false;
		return LosslessFactory.createFromImage(doc, imgSmall);
	}

	@Benchmark
	public PDImageXObject predictor6() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = true;
		return LosslessFactory.createFromImage(doc, imgSmall);
	}


	@Benchmark()
	public PDImageXObject rgbOnly9Big() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = false;
		return LosslessFactory.createFromImage(doc, imgBig);
	}

	@Benchmark
	public PDImageXObject predictor9Big() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = true;
		return LosslessFactory.createFromImage(doc, imgBig);
	}

	@Benchmark()
	public PDImageXObject rgbOnly6Big() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "6");
		LosslessFactory.usePredictorEncoder = false;
		return LosslessFactory.createFromImage(doc, imgBig);
	}

	@Benchmark
	public PDImageXObject predictor6Big() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = true;
		return LosslessFactory.createFromImage(doc, imgBig);

	}

	@Benchmark()
	public PDImageXObject rgbOnly9BigBytes() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = false;
		return LosslessFactory.createFromImage(doc, imgBigBytes);
	}

	@Benchmark
	public PDImageXObject predictor9BigBytes() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = true;
		return LosslessFactory.createFromImage(doc, imgBigBytes);
	}

	@Benchmark()
	public PDImageXObject rgbOnly6BigBytes() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "6");
		LosslessFactory.usePredictorEncoder = false;
		return LosslessFactory.createFromImage(doc, imgBigBytes);
	}

	@Benchmark
	public PDImageXObject predictor6BigBytes() throws IOException
	{
		System.setProperty(Filter.SYSPROP_DEFLATELEVEL, "9");
		LosslessFactory.usePredictorEncoder = true;
		return LosslessFactory.createFromImage(doc, imgBigBytes);
	}

	public static void main(String[] args) throws RunnerException
	{
		Options opt = new OptionsBuilder()
				.include(".*" + LosslessFactoryBenchmark.class.getSimpleName() + ".*")
				.forks(1).build();

		new Runner(opt).run();
	}
}
