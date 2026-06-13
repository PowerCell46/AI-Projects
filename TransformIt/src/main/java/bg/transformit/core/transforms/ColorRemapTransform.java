package bg.transformit.core.transforms;


import bg.transformit.core.Transform;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


/**
 * Remaps image colors in one of two modes:
 *
 * <ul>
 *   <li><b>AUTO_SWAP</b> — quantizes the image into {@code bucketCount} color
 *       buckets via median-cut, ranks them by pixel frequency, then swaps the
 *       most-frequent colors with the least-frequent ones.</li>
 *   <li><b>PALETTE</b> — snaps every pixel to the nearest color in a chosen
 *       preset palette (by squared RGB distance).</li>
 * </ul>
 */
public class ColorRemapTransform implements Transform {

    public enum Mode { AUTO_SWAP, PALETTE }


    private Mode         mode            = Mode.AUTO_SWAP;
    private int          bucketCount     = 8;
    private ColorPalette selectedPalette = ColorPalette.SEPIA;


    @Override
    public BufferedImage apply(BufferedImage src) {
        return switch (mode) {
            case AUTO_SWAP -> applyAutoSwap(src);
            case PALETTE   -> applyPalette(src);
        };
    }

    @Override
    public String displayName() {
        return "Color Remap";
    }

    public Mode         getMode()                         { return mode; }
    public void         setMode(Mode v)                   { mode = v; }

    public int          getBucketCount()                  { return bucketCount; }
    public void         setBucketCount(int v)             { bucketCount = v; }

    public ColorPalette getSelectedPalette()              { return selectedPalette; }
    public void         setSelectedPalette(ColorPalette v){ selectedPalette = v; }

    // =========================================================================
    // AUTO_SWAP
    // =========================================================================

    private BufferedImage applyAutoSwap(BufferedImage src) {
        List<int[]> samples = samplePixels(src);

        int k = Math.max(2, Math.min(bucketCount, samples.size()));
        List<ColorBucket> buckets = medianCut(samples, k);

        // Sort most-frequent first
        buckets.sort(Comparator.comparingInt(ColorBucket::pixelCount).reversed());

        // Build swap map: bucket[i] → bucket[K-1-i]
        int n = buckets.size();
        int[] swappedR = new int[n];
        int[] swappedG = new int[n];
        int[] swappedB = new int[n];

        for (int i = 0; i < n; i++) {
            ColorBucket target = buckets.get(n - 1 - i);
            swappedR[i] = target.avgR();
            swappedG[i] = target.avgG();
            swappedB[i] = target.avgB();
        }

        // Remap every pixel
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb  = src.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int r     = (argb >> 16) & 0xFF;
                int g     = (argb >>  8) & 0xFF;
                int b     =  argb        & 0xFF;

                int idx = nearestBucket(r, g, b, buckets);
                out.setRGB(x, y, (alpha << 24) | (swappedR[idx] << 16) | (swappedG[idx] << 8) | swappedB[idx]);
            }
        }

        return out;
    }

    // =========================================================================
    // PALETTE
    // =========================================================================

    private BufferedImage applyPalette(BufferedImage src) {
        int[] palette = selectedPalette.getColors();

        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb  = src.getRGB(x, y);
                int alpha = (argb >> 24) & 0xFF;
                int r     = (argb >> 16) & 0xFF;
                int g     = (argb >>  8) & 0xFF;
                int b     =  argb        & 0xFF;

                int nearest = nearestPaletteColor(r, g, b, palette);
                out.setRGB(x, y, (alpha << 24) | (nearest & 0x00FFFFFF));
            }
        }

        return out;
    }

    // =========================================================================
    // Median-cut quantization
    // =========================================================================

    /**
     * Splits the pixel list into {@code k} color buckets using the median-cut
     * algorithm: repeatedly find the bucket with the widest color range and
     * split it at the median along that axis.
     */
    private List<ColorBucket> medianCut(List<int[]> pixels, int k) {
        List<List<int[]>> buckets = new ArrayList<>();
        buckets.add(new ArrayList<>(pixels));

        while (buckets.size() < k) {
            int splitIdx = widestRangeBucketIndex(buckets);
            List<int[]> toSplit = buckets.remove(splitIdx);

            if (toSplit.size() <= 1) {
                buckets.add(toSplit);
                break;
            }

            int axis = dominantColorAxis(toSplit);
            toSplit.sort(Comparator.comparingInt(p -> p[axis]));

            int mid = toSplit.size() / 2;
            buckets.add(new ArrayList<>(toSplit.subList(0, mid)));
            buckets.add(new ArrayList<>(toSplit.subList(mid, toSplit.size())));
        }

        return buckets.stream()
                .filter(b -> !b.isEmpty())
                .map(this::toBucket)
                .toList();
    }

    /** Samples representative pixels — up to ~10 000 from large images. */
    private List<int[]> samplePixels(BufferedImage src) {
        int w    = src.getWidth();
        int h    = src.getHeight();
        int step = Math.max(1, (int) Math.sqrt((double) (w * h) / 10_000));

        List<int[]> pixels = new ArrayList<>((w / step) * (h / step));

        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int argb = src.getRGB(x, y);
                pixels.add(new int[]{
                        (argb >> 16) & 0xFF,
                        (argb >>  8) & 0xFF,
                         argb        & 0xFF
                });
            }
        }

        return pixels;
    }

    private int widestRangeBucketIndex(List<List<int[]>> buckets) {
        int maxRange = -1;
        int idx      = 0;

        for (int i = 0; i < buckets.size(); i++) {
            int range = colorRange(buckets.get(i));
            if (range > maxRange) {
                maxRange = range;
                idx      = i;
            }
        }

        return idx;
    }

    /** Returns the max single-channel range across R, G, B for a pixel list. */
    private int colorRange(List<int[]> pixels) {
        int[] mm = minMaxRGB(pixels);
        return Math.max(mm[3] - mm[0], Math.max(mm[4] - mm[1], mm[5] - mm[2]));
    }

    /** Returns the channel index (0=R, 1=G, 2=B) with the largest spread. */
    private int dominantColorAxis(List<int[]> pixels) {
        int[] mm     = minMaxRGB(pixels);
        int   rRange = mm[3] - mm[0];
        int   gRange = mm[4] - mm[1];
        int   bRange = mm[5] - mm[2];

        if (rRange >= gRange && rRange >= bRange) return 0;
        if (gRange >= bRange) return 1;
        return 2;
    }

    /** Returns [minR, minG, minB, maxR, maxG, maxB]. */
    private int[] minMaxRGB(List<int[]> pixels) {
        int[] mm = {255, 255, 255, 0, 0, 0};

        for (int[] p : pixels) {
            mm[0] = Math.min(mm[0], p[0]);
            mm[1] = Math.min(mm[1], p[1]);
            mm[2] = Math.min(mm[2], p[2]);
            mm[3] = Math.max(mm[3], p[0]);
            mm[4] = Math.max(mm[4], p[1]);
            mm[5] = Math.max(mm[5], p[2]);
        }

        return mm;
    }

    private ColorBucket toBucket(List<int[]> pixels) {
        long sr = 0, sg = 0, sb = 0;

        for (int[] p : pixels) {
            sr += p[0];
            sg += p[1];
            sb += p[2];
        }

        int n = pixels.size();
        return new ColorBucket((int)(sr / n), (int)(sg / n), (int)(sb / n), n);
    }

    private int nearestBucket(int r, int g, int b, List<ColorBucket> buckets) {
        int minDist    = Integer.MAX_VALUE;
        int nearestIdx = 0;

        for (int i = 0; i < buckets.size(); i++) {
            int dist = squaredDist(r, g, b, buckets.get(i).avgR(), buckets.get(i).avgG(), buckets.get(i).avgB());
            if (dist < minDist) {
                minDist    = dist;
                nearestIdx = i;
            }
        }

        return nearestIdx;
    }

    private int nearestPaletteColor(int r, int g, int b, int[] palette) {
        int minDist = Integer.MAX_VALUE;
        int best    = palette[0];

        for (int packed : palette) {
            int pr   = (packed >> 16) & 0xFF;
            int pg   = (packed >>  8) & 0xFF;
            int pb   =  packed        & 0xFF;
            int dist = squaredDist(r, g, b, pr, pg, pb);

            if (dist < minDist) {
                minDist = dist;
                best    = packed;
            }
        }

        return best;
    }

    private int squaredDist(int r1, int g1, int b1, int r2, int g2, int b2) {
        int dr = r1 - r2;
        int dg = g1 - g2;
        int db = b1 - b2;
        return dr * dr + dg * dg + db * db;
    }

    // =========================================================================
    // Inner record
    // =========================================================================

    private record ColorBucket(int avgR, int avgG, int avgB, int pixelCount) {}
}
