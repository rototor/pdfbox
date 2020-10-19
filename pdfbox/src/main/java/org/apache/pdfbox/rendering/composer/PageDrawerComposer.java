package org.apache.pdfbox.rendering.composer;

import java.awt.*;

/**
 * Composer for the PageDrawer. Takes care of drawing images a
 */
public abstract class PageDrawerComposer
{
    /**
     * A buffer to compose nested XForms, Tiles etc into something drawable.
     *
     * After you finished drawing on the Graphics2D of this buffer you can paint it
     * with the composer
     */
    public static abstract class ComposeBuffer {
        /**
         * Create a Graphics2D, which allows to paint on this buffer.
         *
         * Note: You can only call this method once per ComposeBuffer.
         * You have to call {@link Graphics2D#dispose()} after you are finished
         * with drawing.
         *
         * @return a Graphics2D to paint on.
         */
        public abstract Graphics2D createGraphics();

        /*
         * Dispose and free this buffer. After calling this, the buffer can no longer be used.
         * This may be used to close resoures hold by this buffer. This is only called once.
         */
        public void dispose()
        {
            /* NOP */
        }
    }

    /**
     * Create a buffer to compose some XForms, Tiles, ... in.
     * @param pixelWith width in pixel.
     * @param pixelHeight height in pixel.
     * @return the ComposeBuffer
     */
    public abstract ComposeBuffer createComposeBuffer(int pixelWith, int pixelHeight) ;


}
