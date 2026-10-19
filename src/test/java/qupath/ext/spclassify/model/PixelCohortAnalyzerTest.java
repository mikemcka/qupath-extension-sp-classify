package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PixelCohortAnalyzerTest {

    /** Build a 2-channel image whose channels share the given summary values. */
    private static ImagePixelStats.ImageStats image(
            String name,
            double fgCoverage,
            double dynamicRange,
            double saturationFraction,
            double emptyFraction,
            double median) {
        return image(name, fgCoverage, dynamicRange, saturationFraction, emptyFraction, median, 100.0);
    }

    /** As {@link #image}, with an explicit focus (Laplacian variance) value. */
    private static ImagePixelStats.ImageStats image(
            String name,
            double fgCoverage,
            double dynamicRange,
            double saturationFraction,
            double emptyFraction,
            double median,
            double focus) {
        double bg = 1.0 - fgCoverage;
        double p1 = 10.0;
        double p99 = p1 + dynamicRange;
        var channels = new ArrayList<ImagePixelStats.ChannelStats>();
        for (String ch : List.of("DAPI", "CD8")) {
            channels.add(new ImagePixelStats.ChannelStats(
                    ch,
                    1_000_000L,
                    median, // mean (≈ median for the test)
                    dynamicRange / 4, // std
                    0.0, // min
                    median, // median
                    p99 + 1, // max
                    p1,
                    p99,
                    saturationFraction,
                    50.0, // otsu threshold
                    bg,
                    fgCoverage,
                    dynamicRange,
                    focus));
        }
        return new ImagePixelStats.ImageStats(name, 4.0, 1024, 1024, channels, emptyFraction);
    }

    /** Six tightly-clustered normal images. */
    private static List<ImagePixelStats.ImageStats> normalCohort() {
        var list = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 6; i++) {
            double jitter = i * 0.3;
            list.add(image(
                    "normal-" + i,
                    0.60 + i * 0.004, // fg coverage ~0.60
                    190.0 + jitter, // dynamic range ~190
                    0.0, // no saturation
                    0.30 + i * 0.004, // empty fraction ~0.30
                    100.0 + jitter)); // median ~100
        }
        return list;
    }

    @Test
    void flagsBackgroundHeavyImage() {
        var inputs = normalCohort();
        inputs.add(image(
                "mostly-background",
                0.08, // very low foreground coverage
                190.0, // normal dynamic range
                0.0,
                0.90, // very high empty fraction
                100.0));

        var report = PixelCohortAnalyzer.analyze(inputs);
        var r = report.byImageName().get("mostly-background");

        assertNotNull(r);
        assertTrue(r.flags().contains(PixelCohortReport.BACKGROUND_HEAVY), "flags=" + r.flags());
        assertEquals("Background-heavy", r.verdict());
        assertTrue(r.narrative().toLowerCase().contains("background"));
        // Worst image sorts to the top.
        assertEquals("mostly-background", report.images().get(0).imageName());
    }

    @Test
    void flagsSaturatedImageEvenWhenBaselineIsZero() {
        var inputs = normalCohort(); // all 0% saturation
        inputs.add(image(
                "over-exposed",
                0.60,
                190.0,
                0.25, // 25% of pixels clipped
                0.30,
                100.0));

        var report = PixelCohortAnalyzer.analyze(inputs);
        var r = report.byImageName().get("over-exposed");

        assertNotNull(r);
        assertTrue(r.flags().contains(PixelCohortReport.SATURATED), "flags=" + r.flags());
        assertTrue(List.of("DAPI", "CD8").contains(r.maxSaturationChannel()));
        assertTrue(r.maxSaturationFraction() >= 0.01);
    }

    @Test
    void flagsWeakSignalImage() {
        var inputs = normalCohort();
        inputs.add(image(
                "washed-out",
                0.60,
                4.0, // almost no dynamic range
                0.0,
                0.30,
                100.0));

        var report = PixelCohortAnalyzer.analyze(inputs);
        var r = report.byImageName().get("washed-out");

        assertNotNull(r);
        assertTrue(r.flags().contains(PixelCohortReport.WEAK_SIGNAL), "flags=" + r.flags());
    }

    @Test
    void leavesNormalImagesUnflagged() {
        var report = PixelCohortAnalyzer.analyze(normalCohort());
        for (var r : report.images()) {
            assertFalse(r.flagged(), r.imageName() + " should not be flagged: " + r.flags());
            assertEquals(PixelCohortReport.VERDICT_OK, r.verdict());
        }
    }

    @Test
    void emptyInputYieldsEmptyReport() {
        assertTrue(PixelCohortAnalyzer.analyze(List.of()).images().isEmpty());
        assertTrue(PixelCohortAnalyzer.analyze(null).images().isEmpty());
    }

    @Test
    void deadChannelNoiseDoesNotFlagAnyImage() {
        // A near-dead channel: every image's median hovers at the noise floor
        // (~0) with one slightly-larger blip. It is below the signal floor, so it
        // must not produce an intensity flag in an otherwise healthy cohort.
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 8; i++) {
            double deadMedian = (i == 0) ? 0.005 : 1e-7 * i;
            inputs.add(deadChannelImage("img-" + i, deadMedian));
        }

        var report = PixelCohortAnalyzer.analyze(inputs);
        for (var r : report.images()) {
            assertFalse(r.flagged(), r.imageName() + " unexpectedly flagged: " + r.flags());
            assertEquals(PixelCohortReport.VERDICT_OK, r.verdict());
        }
    }

    /** Healthy bright channel + a near-dead channel sitting at the noise floor. */
    private static ImagePixelStats.ImageStats deadChannelImage(String name, double deadMedian) {
        return deadChannelImage(name, deadMedian, 0.4);
    }

    /** As above, with an explicit (noise-floor) p99 for the near-dead TCR channel. */
    private static ImagePixelStats.ImageStats deadChannelImage(String name, double deadMedian, double tcrP99) {
        var channels = new ArrayList<ImagePixelStats.ChannelStats>();
        channels.add(new ImagePixelStats.ChannelStats(
                "DAPI", 1_000_000L, 100.0, 47.5, 0.0, 100.0, 201.0, 10.0, 200.0, 0.0, 50.0, 0.40, 0.60, 190.0, 100.0));
        // TCR foreground 0.02 is below the signal floor → excluded from intensity.
        channels.add(new ImagePixelStats.ChannelStats(
                "TCR",
                1_000_000L,
                deadMedian,
                0.01,
                0.0,
                deadMedian,
                tcrP99 + 0.1,
                0.0,
                tcrP99,
                0.0,
                0.05,
                0.98,
                0.02,
                tcrP99,
                1e-7));
        return new ImagePixelStats.ImageStats(name, 4.0, 1024, 1024, channels, 0.30);
    }

    @Test
    void flagsBrightIntensityOutlierOnSignalChannel() {
        // Cohort of normal-brightness slides + one much brighter slide. The bright
        // slide's signal-channel p99 diverges sharply from the cohort.
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 8; i++) {
            inputs.add(image("normal-" + i, 0.60, 190.0 + i * 0.5, 0.0, 0.30, 100.0));
        }
        inputs.add(image("too-bright", 0.60, 600.0, 0.0, 0.30, 100.0));

        var report = PixelCohortAnalyzer.analyze(inputs);
        var r = report.byImageName().get("too-bright");

        assertNotNull(r);
        assertTrue(r.flags().contains(PixelCohortReport.INTENSITY_OUTLIER), "flags=" + r.flags());
        assertEquals("Intensity outlier", r.verdict());
        assertNotNull(r.maxMarkerChannel());
        // Worst image sorts to the top.
        assertEquals("too-bright", report.images().get(0).imageName());
    }

    @Test
    void deadChannelP99SpikeDoesNotTriggerIntensityOutlier() {
        // A 45× relative p99 spike on a sub-floor (near-dead) channel must NOT
        // flag: dead channels are excluded from intensity-outlier detection.
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 8; i++) {
            double tcrP99 = (i == 0) ? 0.9 : 0.02;
            inputs.add(deadChannelImage("img-" + i, 1e-7, tcrP99));
        }

        var report = PixelCohortAnalyzer.analyze(inputs);
        for (var r : report.images()) {
            assertFalse(
                    r.flags().contains(PixelCohortReport.INTENSITY_OUTLIER), r.imageName() + " flagged: " + r.flags());
        }
    }

    @Test
    void lowFocusImageIsSurfacedButNotFlagged() {
        // Focus tracks brightness as much as sharpness, so a low-focus (e.g. dim)
        // image must NOT be flagged on focus alone — the metric is informational.
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 6; i++) {
            inputs.add(image("sharp-" + i, 0.60, 190.0, 0.0, 0.30, 100.0, 100.0 + i * 0.5));
        }
        inputs.add(image("low-focus", 0.60, 190.0, 0.0, 0.30, 100.0, 1.0));

        var report = PixelCohortAnalyzer.analyze(inputs);
        var r = report.byImageName().get("low-focus");

        assertNotNull(r);
        // maxFocus is computed and surfaced …
        assertEquals(1.0, r.maxFocus(), 1e-9);
        // … but it does not produce a verdict.
        assertFalse(r.flagged(), "should not be flagged on focus: " + r.flags());
        assertEquals(PixelCohortReport.VERDICT_OK, r.verdict());
    }

    /** Profile of a tissue fraction {@code f}: background at log 2, tissue at log 6, plus jitter. */
    private static ImagePixelStats.TissueProfile profile(String channel, double f, double jitter) {
        int n = ImagePixelStats.TISSUE_QUANTILES;
        double[] q = new double[n];
        int firstTissue = (int) Math.round((1.0 - f) * (n - 1));
        for (int i = 0; i < n; i++) {
            q[i] = (i < firstTissue ? 2.0 : 6.0) + jitter * i / n;
        }
        return new ImagePixelStats.TissueProfile(channel, 81, q);
    }

    private static ImagePixelStats.ImageStats withProfiles(
            ImagePixelStats.ImageStats s, ImagePixelStats.TissueProfile... profiles) {
        return new ImagePixelStats.ImageStats(
                s.imageName(),
                s.downsample(),
                s.width(),
                s.height(),
                s.channels(),
                s.emptyFraction(),
                null,
                List.of(profiles));
    }

    @Test
    void tissueFromNuclearChannelFlagsNearlyEmptyImage() {
        // All images, including the empty one, have the same all-channel emptyFraction:
        // only the nuclear profile can tell them apart.
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 8; i++) {
            var s = image("core-" + i, 0.60, 190.0, 0.0, 0.10, 100.0);
            inputs.add(withProfiles(s, profile("DAPI", 0.70 + 0.01 * i, 0.1)));
        }
        inputs.add(withProfiles(image("empty", 0.60, 190.0, 0.0, 0.10, 100.0), profile("DAPI", 0.02, 0.1)));

        var report = PixelCohortAnalyzer.analyze(inputs);
        var r = report.byImageName().get("empty");

        assertEquals("DAPI", report.tissueChannel());
        assertTrue(report.tissueLogThreshold() > 2.0 && report.tissueLogThreshold() < 6.0);
        assertEquals(0.02, r.tissueFraction(), 0.002);
        assertEquals("DAPI", r.tissueSource());
        assertTrue(r.flags().contains(PixelCohortReport.BACKGROUND_HEAVY), "flags=" + r.flags());
        assertEquals("empty", report.images().get(0).imageName());
        assertEquals(0.73, report.byImageName().get("core-3").tissueFraction(), 0.002);
        assertFalse(report.byImageName().get("core-3").flagged());
    }

    @Test
    void tissueBelowHardFloorIsFlaggedWithoutCohortOutlier() {
        // Every image is nearly empty, so none is a cohort outlier; the 5% floor still flags.
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 6; i++) {
            var s = image("sparse-" + i, 0.60, 190.0, 0.0, 0.30, 100.0);
            inputs.add(withProfiles(s, profile("DAPI", 0.02 + 0.002 * i, 0.1)));
        }
        var report = PixelCohortAnalyzer.analyze(inputs);
        for (var r : report.images()) {
            assertTrue(r.flags().contains(PixelCohortReport.BACKGROUND_HEAVY), r.imageName() + " " + r.flags());
        }
    }

    @Test
    void tissueChannelIsMostCommonUnlessRequested() {
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 4; i++) {
            var s = image("img-" + i, 0.60, 190.0, 0.0, 0.30, 100.0);
            inputs.add(
                    i == 0
                            ? withProfiles(s, profile("Hoechst", 0.7, 0.1))
                            : withProfiles(s, profile("Hoechst", 0.7, 0.1), profile("DRAQ5", 0.5, 0.1)));
        }
        // DRAQ5 is in 3 images, Hoechst in 4.
        var auto = PixelCohortAnalyzer.analyze(inputs);
        assertEquals(List.of("Hoechst", "DRAQ5"), auto.tissueChannels());
        assertEquals("Hoechst", auto.tissueChannel());

        var chosen = PixelCohortAnalyzer.analyze(inputs, null, "DRAQ5");
        assertEquals("DRAQ5", chosen.tissueChannel());
        assertTrue(Double.isNaN(chosen.byImageName().get("img-0").tissueFraction()));
        assertEquals(0.5, chosen.byImageName().get("img-1").tissueFraction(), 0.002);

        assertEquals(
                "Hoechst", PixelCohortAnalyzer.analyze(inputs, null, "missing").tissueChannel());
    }

    @Test
    void withoutNuclearChannelTissueFallsBackToEmptyFraction() {
        var report = PixelCohortAnalyzer.analyze(normalCohort());
        assertTrue(report.tissueChannel() == null);
        var r = report.byImageName().get("normal-0");
        assertEquals(PixelCohortReport.TISSUE_SOURCE_ALL_CHANNELS, r.tissueSource());
        assertEquals(1.0 - r.emptyFraction(), r.tissueFraction(), 1e-12);
    }

    /** Image with one channel per entry of {@code p99s}, all signal-bearing. */
    private static ImagePixelStats.ImageStats multiChannel(String name, double... p99s) {
        var channels = new ArrayList<ImagePixelStats.ChannelStats>();
        for (int c = 0; c < p99s.length; c++) {
            double p99 = p99s[c];
            channels.add(new ImagePixelStats.ChannelStats(
                    "M" + c, 1000L, p99 / 4, p99 / 8, 0.0, p99 / 4, p99 * 1.1, 1.0, p99, 0.0, p99 / 2, 0.6, 0.4,
                    p99 - 1.0, 100.0));
        }
        return new ImagePixelStats.ImageStats(name, 1.0, 100, 100, channels, 0.3);
    }

    @Test
    void wholeImageBrightnessShiftFlagsButSingleMarkerDoesNot() {
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        var rng = new java.util.Random(7);
        double[] base = {200, 400, 800, 1600, 3200, 6400};
        for (int i = 0; i < 10; i++) {
            double[] p = new double[base.length];
            for (int c = 0; c < p.length; c++) {
                p[c] = base[c] * Math.exp(0.1 * rng.nextGaussian());
            }
            inputs.add(multiChannel("normal-" + i, p));
        }
        double[] bright = new double[base.length];
        double[] oneMarker = base.clone();
        for (int c = 0; c < base.length; c++) {
            bright[c] = base[c] * 3.0;
        }
        oneMarker[2] = base[2] * 10.0;
        inputs.add(multiChannel("all-bright", bright));
        inputs.add(multiChannel("one-marker", oneMarker));

        var report = PixelCohortAnalyzer.analyze(inputs);
        var b = report.byImageName().get("all-bright");
        assertTrue(b.flags().contains(PixelCohortReport.INTENSITY_OUTLIER), "flags=" + b.flags());
        assertEquals(3.0, b.brightnessShift(), 0.4);

        var m = report.byImageName().get("one-marker");
        assertFalse(m.flags().contains(PixelCohortReport.INTENSITY_OUTLIER), "flags=" + m.flags());
        assertEquals("M2", m.maxMarkerChannel());
        assertTrue(m.maxMarkerZ() > 3.0, "z=" + m.maxMarkerZ());
        assertTrue(m.narrative().contains("Most different marker: M2"), m.narrative());
    }

    @Test
    void robustZFallsBackToStdWhenMadIsZero() {
        // Seven zeros and one clear outlier: MAD is 0, so the std fallback must
        // still produce a large positive z for the outlier.
        double[] vals = {0, 0, 0, 0, 0, 0, 0, 0.25};
        double[] z = PixelCohortAnalyzer.robustZ(vals);
        assertTrue(z[7] > 2.5, "outlier z=" + z[7]);
    }
}
