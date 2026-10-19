package qupath.ext.spclassify.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of contextualising per-image {@link ImagePixelStats.ImageStats} against
 * the whole project: robust z-scores, outlier flags, and a plain-English review per
 * image. Produced by {@link PixelCohortAnalyzer}.
 * <p>
 * This is the pixel-level analogue of {@link CohortAnomalyReport}: the same
 * MAD-based robust-z machinery, applied to pixel-intensity statistics instead of
 * cell-class composition.
 *
 * @param images             per-image reports, sorted worst-first by {@link ImageReport#score()}
 * @param tissueChannel      nuclear channel used for tissue fraction, or {@code null}
 *                           when no image has one (tissue then uses {@code emptyFraction})
 * @param tissueChannels     nuclear channels available in the cohort, most common first
 * @param tissueLogThreshold project-wide tissue threshold on {@code log(1 + smoothed value)}
 *                           of {@code tissueChannel}; {@code NaN} when not used
 */
public record PixelCohortReport(
        List<ImageReport> images, String tissueChannel, List<String> tissueChannels, double tissueLogThreshold) {

    // ── Reason codes (stable identifiers used in flags, CSV, and tests) ──────────
    public static final String BACKGROUND_HEAVY = "BACKGROUND_HEAVY";
    public static final String SATURATED = "SATURATED";
    public static final String WEAK_SIGNAL = "WEAK_SIGNAL";
    /** Whole-image brightness differs from the cohort (not a single marker). */
    public static final String INTENSITY_OUTLIER = "INTENSITY_OUTLIER";

    /** Human-readable verdict shown when an image trips no flags. */
    public static final String VERDICT_OK = "OK";

    /** {@link ImageReport#tissueSource()} when no nuclear channel was available. */
    public static final String TISSUE_SOURCE_ALL_CHANNELS = "all channels";

    public PixelCohortReport {
        images = images == null ? List.of() : List.copyOf(images);
        tissueChannels = tissueChannels == null ? List.of() : List.copyOf(tissueChannels);
    }

    /** A report with no tissue-channel information. */
    public PixelCohortReport(List<ImageReport> images) {
        this(images, null, List.of(), Double.NaN);
    }

    /** @return reports keyed by image name (first occurrence wins on duplicates). */
    public Map<String, ImageReport> byImageName() {
        var map = new LinkedHashMap<String, ImageReport>(images.size());
        for (var r : images) {
            map.putIfAbsent(r.imageName(), r);
        }
        return map;
    }

    /**
     * Tunable cutoffs for the verdict rules. All z-score cutoffs are in robust
     * (MAD-scaled) standard deviations.
     *
     * @param backgroundTissueZ       flag BACKGROUND_HEAVY when tissue-fraction z ≤ −this
     * @param backgroundMinTissue     always flag BACKGROUND_HEAVY when the tissue fraction is below this
     * @param saturationZ             flag SATURATED when max saturation-fraction z ≥ this
     *                                (cohort-relative path) …
     * @param saturationMinFraction   …and the max saturation fraction is ≥ this absolute value
     * @param saturationHardFraction  always flag SATURATED when the max saturation
     *                                fraction is ≥ this, regardless of the cohort
     * @param weakSignalZ             flag WEAK_SIGNAL when median dynamic-range z ≤ −this
     * @param brightnessShiftZ        flag INTENSITY_OUTLIER when the whole-image brightness
     *                                shift z magnitude ≥ this
     */
    public record Thresholds(
            double backgroundTissueZ,
            double backgroundMinTissue,
            double saturationZ,
            double saturationMinFraction,
            double saturationHardFraction,
            double weakSignalZ,
            double brightnessShiftZ) {}

    /** Default thresholds. */
    public static final Thresholds DEFAULT_THRESHOLDS = new Thresholds(
            2.5, // backgroundTissueZ
            0.05, // backgroundMinTissue (under 5% tissue → always flag)
            3.0, // saturationZ
            0.01, // saturationMinFraction (1% of pixels clipped)
            0.05, // saturationHardFraction (5% clipped → always flag)
            2.5, // weakSignalZ
            2.5 // brightnessShiftZ
            );

    /**
     * Per-channel context for one image: the raw per-channel statistics, carried
     * through for display and CSV export.
     *
     * @param channel channel name
     * @param stats   the raw per-channel statistics
     */
    public record ChannelContext(String channel, ImagePixelStats.ChannelStats stats) {}

    /**
     * Whole-image report with verdict, flags, the cohort-relative narrative, and
     * image-level summary scalars plus their z-scores.
     *
     * @param imageName              image name
     * @param verdict                short human verdict (e.g. "OK", "Background-heavy")
     * @param flags                  reason codes that fired (may be empty)
     * @param score                  sort key; higher = more anomalous
     * @param narrative              multi-line plain-English review
     * @param tissueFraction         fraction of the image that is tissue
     * @param tissueFractionZ        robust z of the tissue fraction vs cohort
     * @param tissueSource           nuclear channel the tissue fraction came from, or
     *                               {@link #TISSUE_SOURCE_ALL_CHANNELS}; {@code null} when unavailable
     * @param emptyFraction          fraction below the Otsu threshold in every channel
     * @param meanForegroundCoverage mean foreground coverage across channels
     * @param maxSaturationFraction  max saturation fraction across channels
     * @param maxSaturationChannel   channel carrying that max (may be null)
     * @param maxSaturationFractionZ robust z vs cohort
     * @param medianDynamicRange     median dynamic range across channels
     * @param medianDynamicRangeZ    robust z vs cohort
     * @param maxFocus               max per-channel Laplacian variance (informational, never flagged)
     * @param maxFocusZ              robust z of {@link #maxFocus} vs cohort
     * @param brightnessShift        whole-image brightness relative to the cohort: the
     *                               median over signal channels of {@code p99 / cohort median p99}
     *                               (1 = typical); drives INTENSITY_OUTLIER
     * @param brightnessShiftZ       robust z of {@code log(brightnessShift)} vs cohort
     * @param maxMarkerZ             largest-magnitude robust z of {@code log(p99)} over signal
     *                               channels (informational, never flagged)
     * @param maxMarkerChannel       channel carrying {@link #maxMarkerZ} (may be null)
     * @param channels               per-channel context, channel order
     * @param imageId                project entry ID, or {@code null}
     */
    public record ImageReport(
            String imageName,
            String verdict,
            List<String> flags,
            double score,
            String narrative,
            double tissueFraction,
            double tissueFractionZ,
            String tissueSource,
            double emptyFraction,
            double meanForegroundCoverage,
            double maxSaturationFraction,
            String maxSaturationChannel,
            double maxSaturationFractionZ,
            double medianDynamicRange,
            double medianDynamicRangeZ,
            double maxFocus,
            double maxFocusZ,
            double brightnessShift,
            double brightnessShiftZ,
            double maxMarkerZ,
            String maxMarkerChannel,
            List<ChannelContext> channels,
            String imageId) {

        public ImageReport {
            imageName = imageName == null ? "" : imageName;
            verdict = verdict == null ? VERDICT_OK : verdict;
            flags = flags == null ? List.of() : List.copyOf(flags);
            channels = channels == null ? List.of() : List.copyOf(channels);
        }

        /** @return {@code true} if any flag fired. */
        public boolean flagged() {
            return !flags.isEmpty();
        }
    }
}
