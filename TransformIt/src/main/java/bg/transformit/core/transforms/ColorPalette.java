package bg.transformit.core.transforms;


/**
 * Preset color palettes for the {@link ColorRemapTransform} palette mode.
 *
 * <p>Each constant carries a small array of packed {@code 0xRRGGBB} values.
 * Pixels are snapped to the nearest entry by squared RGB distance.
 */
public enum ColorPalette {

    SEPIA(new int[]{
            0x704214, 0x8B5A2B, 0xA67B5B, 0xC4956A, 0xDEB887, 0xF5DEB3
    }),

    GAMEBOY(new int[]{
            0x0F380F, 0x306230, 0x8BAC0F, 0x9BBC0F
    }),

    NEON(new int[]{
            0xFF00FF, 0x00FFFF, 0xFF0090, 0x00FF00, 0xFF6600, 0x6600FF
    }),

    MONO2(new int[]{
            0x000000, 0xFFFFFF
    });


    private final int[] colors;

    ColorPalette(int[] colors) {
        this.colors = colors;
    }

    public int[] getColors() {
        return colors;
    }
}
