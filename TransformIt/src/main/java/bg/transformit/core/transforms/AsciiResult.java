package bg.transformit.core.transforms;


import java.awt.image.BufferedImage;


/**
 * The dual output of {@link AsciiTransform}:
 * <ul>
 *   <li>{@code grid}     — raw character matrix, rows × cols, for {@code .txt} export</li>
 *   <li>{@code rendered} — the chars painted onto a {@link BufferedImage} for {@code .png} export</li>
 * </ul>
 */
public record AsciiResult(char[][] grid, BufferedImage rendered) {

    /** Builds a single-string representation of the grid, rows separated by {@code \n}. */
    public String toText() {
        StringBuilder sb = new StringBuilder(grid.length * (grid[0].length + 1));

        for (char[] row : grid) {
            sb.append(row).append('\n');
        }

        return sb.toString();
    }
}
