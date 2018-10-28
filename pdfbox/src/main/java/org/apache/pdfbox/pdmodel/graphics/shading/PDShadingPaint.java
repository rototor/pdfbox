package org.apache.pdfbox.pdmodel.graphics.shading;

import org.apache.pdfbox.util.Matrix;

/**
 * This interface is implemented by all PDShading-Paints to allow
 * other low level libraries access to the shading source data. One user
 * of this interface is the PdfBoxGraphics2D-adapter.
 */
public interface PDShadingPaint
{
    /**
     * @return the PDShading of this paint
     */
    PDShading getShading();

    /**
     * @return the active Matrix of this paint
     */
    Matrix getMatrix();
}
