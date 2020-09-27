package org.apache.pdfbox.pdmodel.graphics.color;

import java.awt.color.ColorSpace;

/**
 * Represents a PDColor as paintable java.awt.Color
 */
public class PdfColor extends java.awt.Color
{
    private final PDColor pdColor;

    public PdfColor(float r, float g, float b, PDColor color)
    {
        super(r, g, b);
        pdColor = color;
    }

    public PdfColor(float r, float g, float b, float a, PDColor color)
    {
        super(r, g, b, a);
        pdColor = color;
    }

    /**
     * @return the underlying PDColor
     */
    public PDColor getPDColor()
    {
        return pdColor;
    }
}
