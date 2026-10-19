package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImagePixelStatsTest {

    private static final double EPS = 1e-9;

    @Test
    void computesBasicStatisticsOnUniformRamp() {
        // Values 0..100 inclusive (101 values).
        float[] values = new float[101];
        for (int i = 0; i <= 100; i++) {
            values[i] = i;
        }

        var cs = ImagePixelStats.computeChannel("ramp", values, 255.0);

        assertEquals(101, cs.pixelCount());
        assertEquals(0.0, cs.min(), EPS);
        assertEquals(100.0, cs.max(), EPS);
        assertEquals(50.0, cs.mean(), EPS);
        assertEquals(50.0, cs.median(), EPS);
        assertEquals(1.0, cs.p1(), EPS);
        assertEquals(99.0, cs.p99(), EPS);
        assertEquals(98.0, cs.dynamicRange(), EPS);
        // No values near 0.999*255 ≈ 254.7, so nothing is saturated.
        assertEquals(0.0, cs.saturationFraction(), EPS);
    }

    @Test
    void emptyArrayYieldsNaNStatsAndZeroPixels() {
        var cs = ImagePixelStats.computeChannel("empty", new float[0], 255.0);
        assertEquals(0L, cs.pixelCount());
        assertTrue(Double.isNaN(cs.median()));
        assertTrue(Double.isNaN(cs.mean()));
    }

    @Test
    void nanValuesAreIgnored() {
        float[] values = {10f, Float.NaN, 20f, Float.NaN, 30f};
        var cs = ImagePixelStats.computeChannel("withNaN", values, 255.0);
        assertEquals(3L, cs.pixelCount());
        assertEquals(20.0, cs.median(), EPS);
        assertEquals(20.0, cs.mean(), EPS);
    }

    @Test
    void saturationFractionCountsClippedPixels() {
        // 8 mid pixels + 2 fully-saturated (255) out of 10 → 0.2 saturated.
        float[] values = {10f, 20f, 30f, 40f, 50f, 60f, 70f, 80f, 255f, 255f};
        var cs = ImagePixelStats.computeChannel("sat", values, 255.0);
        assertEquals(0.2, cs.saturationFraction(), EPS);
    }

    @Test
    void floatPixelTypeLeavesSaturationUndefined() {
        float[] values = {0.1f, 0.5f, 0.9f, 1.5f};
        var cs = ImagePixelStats.computeChannel("float", values, Double.NaN);
        assertTrue(Double.isNaN(cs.saturationFraction()));
    }

    @Test
    void otsuSplitsClearBimodalDistribution() {
        // Tight low cluster around 10, tight high cluster around 200.
        float[] values = new float[200];
        for (int i = 0; i < 100; i++) {
            values[i] = 10f + (i % 5); // 10..14  (background)
        }
        for (int i = 100; i < 200; i++) {
            values[i] = 200f + (i % 5); // 200..204 (foreground)
        }

        var cs = ImagePixelStats.computeChannel("bimodal", values, 255.0);

        // Threshold must land between the two clusters.
        assertTrue(cs.otsuThreshold() > 14.0 && cs.otsuThreshold() < 200.0, "otsu=" + cs.otsuThreshold());
        // Half background, half foreground.
        assertEquals(0.5, cs.backgroundFraction(), 0.02);
        assertEquals(0.5, cs.foregroundCoverage(), 0.02);
    }

    @Test
    void uniformChannelHasNoBackgroundSplit() {
        float[] values = new float[50];
        java.util.Arrays.fill(values, 42f);
        var cs = ImagePixelStats.computeChannel("flat", values, 255.0);
        // A constant channel has no threshold and no foreground.
        assertTrue(Double.isNaN(cs.otsuThreshold()));
        assertEquals(1.0, cs.backgroundFraction(), EPS);
        assertEquals(0.0, cs.foregroundCoverage(), EPS);
        assertEquals(0.0, cs.dynamicRange(), EPS);
    }

    @Test
    void hotPixelsDoNotSqueezeOtsu() {
        // Half background (~10), half signal (~100), plus 0.05% hot pixels at 60000.
        // With a min..max histogram every real value fell in the first bin and the
        // foreground was only the hot pixels.
        int n = 20_000;
        float[] values = new float[n];
        var rng = new java.util.Random(1);
        for (int i = 0; i < n; i++) {
            values[i] = (i < n / 2 ? 10f : 100f) + (float) rng.nextGaussian() * 2f;
        }
        for (int i = 0; i < 10; i++) {
            values[n - 1 - i] = 60_000f;
        }
        var cs = ImagePixelStats.computeChannel("hot", values, 65535.0);
        // Any threshold in the gap between the two populations gives the same split.
        assertTrue(cs.otsuThreshold() > 15 && cs.otsuThreshold() < 95, "otsu=" + cs.otsuThreshold());
        assertEquals(0.5, cs.foregroundCoverage(), 0.01);
    }

    @Test
    void nuclearChannelNamesAreRecognised() {
        for (String name :
                List.of("DAPI", "HOECHST1", "Hoechst 33342", "DRAQ5", "DAPI (C1)", "SYTO 13", "NucBlue", "RedDot1")) {
            assertTrue(ImagePixelStats.isNuclearChannel(name), name);
        }
        for (String name : List.of("CD3", "Cytokeratin", "Ki67", "Channel 1", "")) {
            assertFalse(ImagePixelStats.isNuclearChannel(name), name);
        }
        assertFalse(ImagePixelStats.isNuclearChannel(null));
    }

    @Test
    void tissueWindowUsesPixelSizeOrImageSize() {
        // 30 µm / 0.377 µm = 79.6 → 80 → odd 81.
        assertEquals(81, ImagePixelStats.tissueWindowPixels(1920, 1440, 0.377));
        // Unknown pixel size: 1% of 2048 = 20.5 → 20 → odd 21.
        assertEquals(21, ImagePixelStats.tissueWindowPixels(2048, 1000, Double.NaN));
        assertEquals(1, ImagePixelStats.tissueWindowPixels(10, 10, 1000.0));
    }

    @Test
    void boxMeanMatchesBruteForceAndSkipsNaN() {
        int w = 9;
        int h = 7;
        int r = 2;
        float[] v = new float[w * h];
        var rng = new java.util.Random(3);
        for (int i = 0; i < v.length; i++) {
            v[i] = rng.nextInt(5) == 0 ? Float.NaN : rng.nextFloat() * 100f;
        }
        float[] got = ImagePixelStats.boxMean(v, w, h, r);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double sum = 0;
                int count = 0;
                for (int yy = Math.max(0, y - r); yy <= Math.min(h - 1, y + r); yy++) {
                    for (int xx = Math.max(0, x - r); xx <= Math.min(w - 1, x + r); xx++) {
                        float f = v[yy * w + xx];
                        if (!Float.isNaN(f)) {
                            sum += f;
                            count++;
                        }
                    }
                }
                assertEquals(sum / count, got[y * w + x], 1e-3, "x=" + x + " y=" + y);
            }
        }
    }

    @Test
    void computeStoresTissueProfilesForNuclearChannelsOnly() {
        int w = 20;
        int h = 10;
        float[] dapi = new float[w * h];
        float[] cd8 = new float[w * h];
        // Left half nuclei-rich (1000), right half empty (5).
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dapi[y * w + x] = x < w / 2 ? 1000f : 5f;
            }
        }
        var image = ImagePixelStats.compute(
                "img", 1.0, w, h, List.of("DAPI", "CD8"), new float[][] {dapi, cd8}, 65535.0, 30.0);

        assertEquals(1, image.tissueProfiles().size());
        var tp = image.tissueProfile("DAPI");
        assertEquals(1, tp.windowPixels());
        double[] q = tp.logQuantiles();
        assertEquals(ImagePixelStats.TISSUE_QUANTILES, q.length);
        for (int i = 1; i < q.length; i++) {
            assertTrue(q[i] >= q[i - 1]);
        }
        assertEquals(0.5, tp.fractionAtOrAbove(Math.log1p(500)), 0.002);
        assertTrue(image.tissueProfile("CD8") == null);
    }

    @Test
    void laplacianVarianceIsHigherForSharpThanBlurredImage() {
        // 8×8 high-contrast checkerboard (sharp) vs a smooth gradient (blurred).
        int w = 8;
        int h = 8;
        float[] checker = new float[w * h];
        float[] gradient = new float[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                checker[y * w + x] = ((x + y) % 2 == 0) ? 0f : 255f;
                gradient[y * w + x] = (x + y) * 4f; // slowly varying
            }
        }

        double sharp = ImagePixelStats.laplacianVariance(checker, w, h);
        double blurred = ImagePixelStats.laplacianVariance(gradient, w, h);

        assertTrue(sharp > blurred, "sharp=" + sharp + " blurred=" + blurred);
        // Unknown shape (0×0) leaves the focus metric undefined.
        assertTrue(Double.isNaN(ImagePixelStats.laplacianVariance(checker, 0, 0)));
    }

    @Test
    void computeChannelFillsFocusWhenShapeKnown() {
        float[] values = new float[16];
        for (int i = 0; i < 16; i++) {
            values[i] = (i % 2 == 0) ? 0f : 100f;
        }
        // Without a shape, focus is NaN; with a 4×4 shape it is finite.
        assertTrue(
                Double.isNaN(ImagePixelStats.computeChannel("x", values, 255.0).laplacianVariance()));
        var cs = ImagePixelStats.computeChannel("x", values, 255.0, 4, 4);
        assertTrue(cs.laplacianVariance() > 0.0, "focus=" + cs.laplacianVariance());
    }

    @Test
    void emptyFractionCountsPixelsBackgroundInAllChannels() {
        // 4 pixels, 2 channels. Otsu thresholds will sit between low/high clusters.
        // Build channels so pixel 0 is background in both → empty.
        // Channel A: [5, 5, 200, 200]; Channel B: [5, 200, 5, 200].
        float[] chA = {5f, 5f, 200f, 200f};
        float[] chB = {5f, 200f, 5f, 200f};

        var image = ImagePixelStats.compute("img", 4.0, 2, 2, List.of("A", "B"), new float[][] {chA, chB}, 255.0);

        // Only pixel 0 (5,5) is background in both channels → 1/4.
        assertEquals(0.25, image.emptyFraction(), 0.001);
        assertEquals(2, image.channels().size());
    }

    @Test
    void computeProducesOneStatsPerChannel() {
        float[] chA = {1f, 2f, 3f, 4f};
        float[] chB = {10f, 20f, 30f, 40f};
        var image =
                ImagePixelStats.compute("img", 8.0, 2, 2, List.of("DAPI", "CD8"), new float[][] {chA, chB}, 65535.0);

        assertEquals("img", image.imageName());
        assertEquals(8.0, image.downsample(), EPS);
        assertEquals(2, image.channels().size());
        assertEquals("DAPI", image.channels().get(0).channel());
        assertEquals("CD8", image.channels().get(1).channel());
    }
}
