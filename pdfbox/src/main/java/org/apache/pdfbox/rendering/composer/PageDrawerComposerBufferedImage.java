package org.apache.pdfbox.rendering.composer;

import java.awt.*;
import java.awt.image.BufferedImage;

public abstract class PageDrawerComposerBufferedImage extends PageDrawerComposer
{

    public static class BufferedImageComposeBufferBase extends ComposeBuffer
    {
        private final BufferedImage image;

        public BufferedImageComposeBufferBase(BufferedImage image)
        {
            this.image = image;
        }

        @Override
        public Graphics2D createGraphics()
        {
            return image.createGraphics();
        }

        @Override
        public void dispose()
        {
            /*
             * Release some resources of the image
             */
            this.image.flush();
        }
    }

}
