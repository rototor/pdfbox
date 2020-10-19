package org.apache.pdfbox.rendering.composer;

import java.awt.image.BufferedImage;

public class PageDrawerComposerBufferedImageSRGB extends PageDrawerComposerBufferedImage
{
    @Override
    public ComposeBuffer createComposeBuffer(int pixelWith, int pixelHeight)
    {
        BufferedImage bufferedImage = new BufferedImage(pixelWith, pixelHeight,
                BufferedImage.TYPE_INT_ARGB);
        return new BufferedImageComposeBufferBase(bufferedImage);
    }
}
