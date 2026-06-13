package bg.transformit.core.transforms;


import bg.transformit.core.Transform;

import java.awt.*;
import java.awt.image.BufferedImage;


/**
 * Converts the image into a grid of ASCII characters and paints that grid onto
 * a {@link BufferedImage}.  Must be the <em>last</em> transform in the
 * pipeline — nothing meaningful can run after rendered text.
 *
 * <p><b>Density</b> ({@code blockSize}) controls how many pixels each
 * character represents.  A block of {@code blockSize × blockSize} pixels maps
 * to one character whose brightness selects a position in the character ramp.
 *
 * <p><b>Aspect correction</b>: because monospace cells are roughly twice as
 * tall as they are wide, the row count is derived from the column count using
 * a cell-aspect factor (≈ 0.45) so shapes are not vertically squashed.
 *
 * <p>After each {@link #apply} call the result is also available via
 * {@link #getLastResult()} so the pipeline can extract the raw character grid
 * for {@code .txt} export.
 */
public class AsciiTransform implements Transform {

    /** Light-to-dark brightness ramp — index 0 is the brightest character. */
    private static final String RAMP = " .,:;i1tfLCG08@";

    /** Typical monospace charWidth / charHeight ≈ 0.45. */
    private static final double CHAR_ASPECT = 0.45;

    private static final int  FONT_SIZE = 12;
    private static final Font MONO_FONT = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE);


    private int     blockSize  = 5;
    private boolean inverted   = false;
    private boolean colored    = false;

    /** The output from the most recent {@link #apply} call; non-null after first call. */
    private AsciiResult lastResult;


    @Override
    public BufferedImage apply(BufferedImage src) {
        int imgW = src.getWidth();
        int imgH = src.getHeight();

        // Derive grid dimensions with aspect correction
        int cols = Math.max(1, imgW / blockSize);
        int rows = Math.max(1, (int) Math.round(cols * ((double) imgH / imgW) * CHAR_ASPECT));

        double blockW = (double) imgW / cols;
        double blockH = (double) imgH / rows;

        String ramp    = buildRamp();
        int    rampLen = ramp.length();

        char[][] grid     = new char[rows][cols];
        int[][]  blockRgb = colored ? new int[rows][cols] : null;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x0 = (int) (col * blockW);
                int y0 = (int) (row * blockH);
                int x1 = Math.min((int) ((col + 1) * blockW), imgW);
                int y1 = Math.min((int) ((row + 1) * blockH), imgH);

                long sumLuma = 0, sumR = 0, sumG = 0, sumB = 0;
                int  count   = 0;

                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int argb = src.getRGB(x, y);
                        int r    = (argb >> 16) & 0xFF;
                        int g    = (argb >>  8) & 0xFF;
                        int b    =  argb        & 0xFF;

                        sumLuma += (int) (0.299 * r + 0.587 * g + 0.114 * b);

                        if (colored) {
                            sumR += r;
                            sumG += g;
                            sumB += b;
                        }

                        count++;
                    }
                }

                if (count == 0) {
                    grid[row][col] = ' ';
                    continue;
                }

                double avgLuma = (double) sumLuma / count;
                int    rampIdx = (int) ((avgLuma / 255.0) * (rampLen - 1));
                grid[row][col] = ramp.charAt(rampIdx);

                if (colored) {
                    int avgR = (int) (sumR / count);
                    int avgG = (int) (sumG / count);
                    int avgB = (int) (sumB / count);
                    blockRgb[row][col] = (0xFF << 24) | (avgR << 16) | (avgG << 8) | avgB;
                }
            }
        }

        BufferedImage rendered = renderGrid(grid, blockRgb, rows, cols);
        lastResult = new AsciiResult(grid, rendered);
        return rendered;
    }

    @Override
    public String displayName() {
        return "ASCII Art";
    }

    public int     getBlockSize()           { return blockSize; }
    public void    setBlockSize(int v)      { blockSize = v; }

    public boolean isInverted()             { return inverted; }
    public void    setInverted(boolean v)   { inverted = v; }

    public boolean isColored()              { return colored; }
    public void    setColored(boolean v)    { colored = v; }

    public AsciiResult getLastResult()      { return lastResult; }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String buildRamp() {
        return inverted
                ? new StringBuilder(RAMP).reverse().toString()
                : RAMP;
    }

    /**
     * Paints the character grid onto a {@link BufferedImage}.
     *
     * <p>In colored mode each character is drawn in the average color of its
     * source block.  In mono mode all characters are white on black.
     */
    private BufferedImage renderGrid(char[][] grid, int[][] blockRgb, int rows, int cols) {
        FontMetrics metrics = getFontMetrics();
        int charW  = metrics.charWidth('M');
        int charH  = metrics.getHeight();
        int ascent = metrics.getAscent();

        int canvasW = cols * charW;
        int canvasH = rows * charH;

        BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D    g      = canvas.createGraphics();

        g.setFont(MONO_FONT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.BLACK);
        g.fillRect(0, 0, canvasW, canvasH);

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                Color charColor = (blockRgb != null)
                        ? new Color(blockRgb[row][col], true)
                        : Color.WHITE;

                g.setColor(charColor);
                g.drawString(String.valueOf(grid[row][col]), col * charW, row * charH + ascent);
            }
        }

        g.dispose();
        return canvas;
    }

    /** Obtains font metrics using a small temporary image (avoids a Toolkit dependency). */
    private FontMetrics getFontMetrics() {
        BufferedImage probe  = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D    probeG = probe.createGraphics();
        probeG.setFont(MONO_FONT);
        FontMetrics metrics = probeG.getFontMetrics();
        probeG.dispose();
        return metrics;
    }
}
