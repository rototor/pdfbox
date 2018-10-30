package org.apache.pdfbox.pdmodel.graphics.shading;

import org.apache.pdfbox.util.Matrix;

import java.awt.*;

/**
 * This is base class for all PDShading-Paints to allow other low level libraries
 * access to the shading source data. One * user of this interface is the
 * PdfBoxGraphics2D-adapter.
 */
public abstract class ShadingPaint<TPDShading extends PDShading> implements Paint
{
    protected final TPDShading shading;
    protected final Matrix matrix;

    ShadingPaint(TPDShading shading, Matrix matrix)
    {
        this.shading = shading;
        this.matrix = matrix;
    }

    /**
     * @return the PDShading of this paint
     */
    public TPDShading getShading()
    {
        return shading;
    }

    /**
     * @return the active Matrix of this paint
     */
    public Matrix getMatrix()
    {
        return matrix;
    }
}
