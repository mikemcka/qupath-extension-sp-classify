package qupath.ext.spclassify.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import qupath.ext.spclassify.util.RobustStats;

/**
 * Contextualises whole-image pixel statistics against the whole project.
 * <p>
 * Part of the image pixel prescreen, whose statistics methodology was adapted from
 * <a href="https://pypi.org/project/pixel-patrol/">pixel-patrol</a> (MIT) — see
 * {@link ImagePixelStats} for the attribution note.
 * <p>
 * Robust z-scores ({@code z = 0.6745 · (x − median) / MAD}, as in
 * {@link CohortAnomalyAnalyzer}) are computed across the cohort for:
 * <ul>
 *   <li><b>tissue fraction</b> — from the nuclear channel's
 *       {@link ImagePixelStats.TissueProfile}, thresholded at one project-wide
 *       Otsu threshold fitted on the pooled profiles (every image weighted
 *       equally). A per-image threshold would split an empty image at its own
 *       noise level. Without a nuclear channel, {@code 1 − emptyFraction} is used.</li>
 *   <li><b>peak saturation</b> and <b>median dynamic range</b> (unchanged).</li>
 *   <li><b>brightness shift</b> — the median, over signal channels, of
 *       {@code log(p99) − cohort median log(p99)}: how much brighter or dimmer the
 *       whole image is. Single-marker differences are reported
 *       ({@link PixelCohortReport.ImageReport#maxMarkerZ()}) but never flag, because
 *       the p99 of one marker mostly reflects which cells the image contains.</li>
 *   <li><b>focus</b> (reported, never flagged — it tracks brightness as well as sharpness).</li>
 * </ul>
 * Signal channels are those whose cohort-median foreground coverage is at least
 * {@code SIGNAL_FOREGROUND_FLOOR}; near-dead channels would turn small differences
 * into very large z-scores. Like {@link CohortAnomalyAnalyzer}, this class is free
 * of any QuPath/JavaFX dependency and is unit-tested in isolation.
 */
public final class PixelCohortAnalyzer {

    /**
     * A channel takes part in brightness comparisons only if its cohort-median
     * foreground coverage clears this floor. Near-dead / mostly-background markers
     * (whose p99 sits at the noise floor) are excluded.
     */
    private static final double SIGNAL_FOREGROUND_FLOOR = 0.05;

    /** Single-marker |z| at or above which the review names the marker (informational). */
    private static final double MARKER_NOTE_Z = 3.0;

    private PixelCohortAnalyzer() {}

    /** Analyse with {@link PixelCohortReport#DEFAULT_THRESHOLDS} and the automatic tissue channel. */
    public static PixelCohortReport analyze(List<ImagePixelStats.ImageStats> inputs) {
        return analyze(inputs, PixelCohortReport.DEFAULT_THRESHOLDS, null);
    }

    /** Analyse with the automatic tissue channel. */
    public static PixelCohortReport analyze(
            List<ImagePixelStats.ImageStats> inputs, PixelCohortReport.Thresholds thresholds) {
        return analyze(inputs, thresholds, null);
    }

    /**
     * Analyse a cohort of per-image pixel statistics.
     *
     * @param inputs        per-image statistics (nulls are skipped)
     * @param thresholds    verdict cutoffs; {@code null} uses the defaults
     * @param tissueChannel nuclear channel to measure tissue with; {@code null} or a
     *                      channel no image has picks the one present in most images
     * @return the cohort report, images sorted worst-first
     */
    public static PixelCohortReport analyze(
            List<ImagePixelStats.ImageStats> inputs, PixelCohortReport.Thresholds thresholds, String tissueChannel) {

        if (thresholds == null) {
            thresholds = PixelCohortReport.DEFAULT_THRESHOLDS;
        }

        var safe = new ArrayList<ImagePixelStats.ImageStats>();
        if (inputs != null) {
            for (var in : inputs) {
                if (in != null) {
                    safe.add(in);
                }
            }
        }
        if (safe.isEmpty()) {
            return new PixelCohortReport(List.of());
        }

        int nImages = safe.size();

        // ── Tissue fraction ────────────────────────────────────────────────────
        List<String> candidates = tissueChannelCandidates(safe);
        String chosen = (tissueChannel != null && candidates.contains(tissueChannel))
                ? tissueChannel
                : (candidates.isEmpty() ? null : candidates.get(0));
        double tissueThreshold = Double.NaN;
        double[] tissue = new double[nImages];
        String[] tissueSource = new String[nImages];
        if (chosen != null) {
            var profiles = new ArrayList<double[]>();
            for (var img : safe) {
                var tp = img.tissueProfile(chosen);
                if (tp != null) {
                    profiles.add(tp.logQuantiles());
                }
            }
            tissueThreshold = cohortTissueThreshold(profiles);
            for (int i = 0; i < nImages; i++) {
                var tp = safe.get(i).tissueProfile(chosen);
                tissue[i] = tp == null ? Double.NaN : tp.fractionAtOrAbove(tissueThreshold);
                tissueSource[i] = Double.isNaN(tissue[i]) ? null : chosen;
            }
        } else {
            for (int i = 0; i < nImages; i++) {
                double empty = safe.get(i).emptyFraction();
                tissue[i] = Double.isNaN(empty) ? Double.NaN : 1.0 - empty;
                tissueSource[i] = Double.isNaN(tissue[i]) ? null : PixelCohortReport.TISSUE_SOURCE_ALL_CHANNELS;
            }
        }

        // ── Other image-level summary scalars ───────────────────────────────────
        double[] meanForeground = new double[nImages];
        double[] maxSaturation = new double[nImages];
        String[] maxSaturationChannel = new String[nImages];
        double[] medianDynamicRange = new double[nImages];
        double[] maxFocus = new double[nImages];

        for (int i = 0; i < nImages; i++) {
            var img = safe.get(i);
            var fgList = new ArrayList<Double>();
            var dynList = new ArrayList<Double>();
            double bestSat = Double.NaN;
            String bestSatCh = null;
            double bestFocus = Double.NaN;
            for (var cs : img.channels()) {
                if (!Double.isNaN(cs.foregroundCoverage())) {
                    fgList.add(cs.foregroundCoverage());
                }
                if (!Double.isNaN(cs.dynamicRange())) {
                    dynList.add(cs.dynamicRange());
                }
                double sat = cs.saturationFraction();
                if (!Double.isNaN(sat) && (Double.isNaN(bestSat) || sat > bestSat)) {
                    bestSat = sat;
                    bestSatCh = cs.channel();
                }
                // Image focus = the sharpest channel.
                double focus = cs.laplacianVariance();
                if (!Double.isNaN(focus) && (Double.isNaN(bestFocus) || focus > bestFocus)) {
                    bestFocus = focus;
                }
            }
            meanForeground[i] = mean(fgList);
            medianDynamicRange[i] = medianOf(dynList);
            maxSaturation[i] = bestSat;
            maxSaturationChannel[i] = bestSatCh;
            maxFocus[i] = bestFocus;
        }

        // ── Brightness: per-channel log(p99) on signal channels ─────────────────
        var channelOrder = new LinkedHashSet<String>();
        for (var img : safe) {
            for (var cs : img.channels()) {
                channelOrder.add(cs.channel());
            }
        }
        var signalChannels = new ArrayList<String>();
        var logDevByChannel = new ArrayList<double[]>(); // log(p99) − cohort median, per image
        var markerZByChannel = new ArrayList<double[]>();
        var medP99ByChannel = new LinkedHashMap<String, Double>();
        for (String ch : channelOrder) {
            double[] logP99 = new double[nImages];
            double[] fg = new double[nImages];
            double[] p99 = new double[nImages];
            for (int i = 0; i < nImages; i++) {
                var cs = channelOf(safe.get(i), ch);
                p99[i] = cs == null ? Double.NaN : cs.p99();
                logP99[i] = (cs == null || !(cs.p99() > 0)) ? Double.NaN : Math.log(cs.p99());
                fg[i] = cs == null ? Double.NaN : cs.foregroundCoverage();
            }
            double medFg = medianIgnoreNaN(fg);
            double medLog = medianIgnoreNaN(logP99);
            if (Double.isNaN(medFg) || medFg < SIGNAL_FOREGROUND_FLOOR || Double.isNaN(medLog)) {
                continue;
            }
            double[] dev = new double[nImages];
            for (int i = 0; i < nImages; i++) {
                dev[i] = logP99[i] - medLog;
            }
            signalChannels.add(ch);
            logDevByChannel.add(dev);
            markerZByChannel.add(robustZ(logP99));
            medP99ByChannel.put(ch, medianIgnoreNaN(p99));
        }
        double[] logShift = new double[nImages];
        int[] shiftChannels = new int[nImages];
        double[] maxMarkerZ = new double[nImages];
        String[] maxMarkerChannel = new String[nImages];
        for (int i = 0; i < nImages; i++) {
            double[] devs = new double[signalChannels.size()];
            int n = 0;
            double bestAbs = 0.0;
            for (int c = 0; c < signalChannels.size(); c++) {
                double d = logDevByChannel.get(c)[i];
                if (!Double.isNaN(d)) {
                    devs[n++] = d;
                }
                double z = markerZByChannel.get(c)[i];
                if (!Double.isNaN(z) && Math.abs(z) > bestAbs) {
                    bestAbs = Math.abs(z);
                    maxMarkerZ[i] = z;
                    maxMarkerChannel[i] = signalChannels.get(c);
                }
            }
            logShift[i] = n == 0 ? Double.NaN : medianIgnoreNaN(Arrays.copyOf(devs, n));
            shiftChannels[i] = n;
            if (maxMarkerChannel[i] == null) {
                maxMarkerZ[i] = Double.NaN;
            }
        }

        double[] tissueZ = robustZ(tissue);
        double[] maxSaturationZ = robustZ(maxSaturation);
        double[] medianDynamicRangeZ = robustZ(medianDynamicRange);
        double[] maxFocusZ = robustZ(maxFocus);
        double[] shiftZ = robustZ(logShift);

        var ctx = new Context(
                medianIgnoreNaN(tissue),
                medianIgnoreNaN(maxSaturation),
                medianIgnoreNaN(medianDynamicRange),
                medianIgnoreNaN(maxFocus),
                medP99ByChannel);

        // ── Build per-image reports ─────────────────────────────────────────────
        var reports = new ArrayList<PixelCohortReport.ImageReport>(nImages);
        for (int i = 0; i < nImages; i++) {
            var img = safe.get(i);

            var channelContexts = new ArrayList<PixelCohortReport.ChannelContext>(
                    img.channels().size());
            for (var cs : img.channels()) {
                channelContexts.add(new PixelCohortReport.ChannelContext(cs.channel(), cs));
            }

            var flags = new ArrayList<String>(4);
            boolean backgroundHeavy = le(tissueZ[i], -thresholds.backgroundTissueZ())
                    || (!Double.isNaN(tissue[i]) && tissue[i] < thresholds.backgroundMinTissue());
            if (backgroundHeavy) {
                flags.add(PixelCohortReport.BACKGROUND_HEAVY);
            }
            boolean saturated = !Double.isNaN(maxSaturation[i])
                    && ((maxSaturation[i] >= thresholds.saturationMinFraction()
                                    && ge(maxSaturationZ[i], thresholds.saturationZ()))
                            || maxSaturation[i] >= thresholds.saturationHardFraction());
            if (saturated) {
                flags.add(PixelCohortReport.SATURATED);
            }
            boolean weakSignal = le(medianDynamicRangeZ[i], -thresholds.weakSignalZ());
            if (weakSignal) {
                flags.add(PixelCohortReport.WEAK_SIGNAL);
            }
            boolean intensityOutlier = !Double.isNaN(shiftZ[i]) && Math.abs(shiftZ[i]) >= thresholds.brightnessShiftZ();
            if (intensityOutlier) {
                flags.add(PixelCohortReport.INTENSITY_OUTLIER);
            }

            String verdict = verdict(flags);

            double absShiftZ = Double.isNaN(shiftZ[i]) ? 0.0 : Math.abs(shiftZ[i]);
            double score = relu(-tissueZ[i])
                    + (saturated ? relu(maxSaturationZ[i]) : 0.0)
                    + relu(-medianDynamicRangeZ[i])
                    + relu(absShiftZ - 2.0);

            double brightnessShift = Double.isNaN(logShift[i]) ? Double.NaN : Math.exp(logShift[i]);
            var row = new Row(
                    tissue[i],
                    tissueZ[i],
                    tissueSource[i],
                    maxSaturation[i],
                    maxSaturationChannel[i],
                    maxSaturationZ[i],
                    medianDynamicRange[i],
                    medianDynamicRangeZ[i],
                    maxFocus[i],
                    maxFocusZ[i],
                    brightnessShift,
                    shiftZ[i],
                    shiftChannels[i],
                    maxMarkerZ[i],
                    maxMarkerChannel[i]);
            String narrative = buildNarrative(img, verdict, flags, row, ctx);

            reports.add(new PixelCohortReport.ImageReport(
                    img.imageName(),
                    verdict,
                    flags,
                    score,
                    narrative,
                    tissue[i],
                    tissueZ[i],
                    tissueSource[i],
                    img.emptyFraction(),
                    meanForeground[i],
                    maxSaturation[i],
                    maxSaturationChannel[i],
                    maxSaturationZ[i],
                    medianDynamicRange[i],
                    medianDynamicRangeZ[i],
                    maxFocus[i],
                    maxFocusZ[i],
                    brightnessShift,
                    shiftZ[i],
                    maxMarkerZ[i],
                    maxMarkerChannel[i],
                    channelContexts,
                    img.imageId()));
        }

        reports.sort(Comparator.comparingDouble(PixelCohortReport.ImageReport::score)
                .reversed()
                .thenComparing(PixelCohortReport.ImageReport::imageName));

        return new PixelCohortReport(reports, chosen, candidates, tissueThreshold);
    }

    /**
     * Nuclear channels with a tissue profile in at least one image, ordered by the
     * number of images that have them (most first), then by first appearance.
     */
    static List<String> tissueChannelCandidates(List<ImagePixelStats.ImageStats> images) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var img : images) {
            var seen = new java.util.HashSet<String>();
            for (var tp : img.tissueProfiles()) {
                if (tp.channel() != null && seen.add(tp.channel())) {
                    counts.merge(tp.channel(), 1, Integer::sum);
                }
            }
        }
        var names = new ArrayList<>(counts.keySet());
        // Stable sort keeps first-appearance order among equal counts.
        names.sort(Comparator.comparingInt((String n) -> counts.get(n)).reversed());
        return names;
    }

    /**
     * Otsu threshold (0.1–99.9 percentile histogram range) on the pooled tissue
     * profiles. Every profile has the same number of percentiles, so every image
     * carries equal weight whatever its size.
     *
     * @return the threshold on the log scale, or {@code NaN} when there is no data
     *         or every value is equal
     */
    static double cohortTissueThreshold(List<double[]> profiles) {
        int total = 0;
        for (double[] p : profiles) {
            total += p.length;
        }
        double[] pooled = new double[total];
        int k = 0;
        for (double[] p : profiles) {
            System.arraycopy(p, 0, pooled, k, p.length);
            k += p.length;
        }
        Arrays.sort(pooled);
        return ImagePixelStats.otsuThresholdClipped(pooled);
    }

    // ── Narrative ───────────────────────────────────────────────────────────────

    /** Cohort medians used in the narrative. */
    private record Context(
            double medTissue,
            double medMaxSaturation,
            double medDynamicRange,
            double medMaxFocus,
            Map<String, Double> medP99ByChannel) {}

    /** One image's values used in the narrative. */
    private record Row(
            double tissue,
            double tissueZ,
            String tissueSource,
            double maxSaturation,
            String maxSaturationChannel,
            double maxSaturationZ,
            double medianDynamicRange,
            double medianDynamicRangeZ,
            double maxFocus,
            double maxFocusZ,
            double brightnessShift,
            double brightnessShiftZ,
            int brightnessChannels,
            double maxMarkerZ,
            String maxMarkerChannel) {}

    private static String buildNarrative(
            ImagePixelStats.ImageStats img, String verdict, List<String> flags, Row r, Context ctx) {

        var sb = new StringBuilder();
        sb.append(img.imageName()).append(" — ").append(verdict).append('\n');

        if (flags.contains(PixelCohortReport.BACKGROUND_HEAVY) || notable(r.tissueZ())) {
            sb.append("• Tissue ")
                    .append(fmtPct(r.tissue()))
                    .append(" of the image (cohort median ")
                    .append(fmtPct(ctx.medTissue()))
                    .append(", ")
                    .append(fmtZ(r.tissueZ()))
                    .append(" MAD), ");
            if (PixelCohortReport.TISSUE_SOURCE_ALL_CHANNELS.equals(r.tissueSource())) {
                sb.append("measured as pixels above background in at least one channel (no nuclear channel).\n");
            } else {
                sb.append("measured on the smoothed ").append(r.tissueSource()).append(" channel.\n");
            }
        }

        if (flags.contains(PixelCohortReport.SATURATED) || notable(r.maxSaturationZ())) {
            sb.append("• Peak saturation ").append(fmtPct(r.maxSaturation()));
            if (r.maxSaturationChannel() != null) {
                sb.append(" in ").append(r.maxSaturationChannel());
            }
            sb.append(" (cohort median ")
                    .append(fmtPct(ctx.medMaxSaturation()))
                    .append(", ")
                    .append(fmtZ(r.maxSaturationZ()))
                    .append(" MAD).\n");
        }

        if (flags.contains(PixelCohortReport.WEAK_SIGNAL) || notable(r.medianDynamicRangeZ())) {
            sb.append("• Median dynamic range ")
                    .append(fmtNum(r.medianDynamicRange()))
                    .append(" (cohort median ")
                    .append(fmtNum(ctx.medDynamicRange()))
                    .append(", ")
                    .append(fmtZ(r.medianDynamicRangeZ()))
                    .append(" MAD).\n");
        }

        if (notable(r.maxFocusZ())) {
            sb.append("• Focus (sharpest-channel Laplacian variance) ")
                    .append(fmtNum(r.maxFocus()))
                    .append(" (cohort median ")
                    .append(fmtNum(ctx.medMaxFocus()))
                    .append(", ")
                    .append(fmtZ(r.maxFocusZ()))
                    .append(" MAD).\n");
        }

        if (flags.contains(PixelCohortReport.INTENSITY_OUTLIER) || notable(r.brightnessShiftZ())) {
            sb.append("• Overall brightness ")
                    .append(fmtRatio(r.brightnessShift()))
                    .append(" the cohort (median p99 ratio over ")
                    .append(r.brightnessChannels())
                    .append(" signal channels, ")
                    .append(fmtZ(r.brightnessShiftZ()))
                    .append(" MAD).\n");
        }

        if (r.maxMarkerChannel() != null
                && !Double.isNaN(r.maxMarkerZ())
                && Math.abs(r.maxMarkerZ()) >= MARKER_NOTE_Z) {
            double imgP99 = Double.NaN;
            var cs = channelOf(img, r.maxMarkerChannel());
            if (cs != null) {
                imgP99 = cs.p99();
            }
            sb.append("• Most different marker: ")
                    .append(r.maxMarkerChannel())
                    .append(" p99 ")
                    .append(fmtNum(imgP99))
                    .append(" (cohort median ")
                    .append(fmtNum(ctx.medP99ByChannel().getOrDefault(r.maxMarkerChannel(), Double.NaN)))
                    .append(", ")
                    .append(fmtZ(r.maxMarkerZ()))
                    .append(" MAD on a log scale). Not flagged: one marker's brightness mostly reflects")
                    .append(" which cells the image contains.\n");
        }

        if (flags.isEmpty()) {
            sb.append("• Pixel statistics are within the normal range for this project.\n");
        }

        sb.append("Suggested action: ").append(suggestedAction(flags));
        return sb.toString();
    }

    private static String suggestedAction(List<String> flags) {
        if (flags.isEmpty()) {
            return "include — looks normal.";
        }
        if (flags.contains(PixelCohortReport.BACKGROUND_HEAVY)) {
            return "review / consider removing — little tissue.";
        }
        if (flags.contains(PixelCohortReport.SATURATED)) {
            return "review acquisition — channel(s) clipped/over-exposed.";
        }
        if (flags.contains(PixelCohortReport.WEAK_SIGNAL)) {
            return "review staining/exposure — unusually flat signal.";
        }
        if (flags.contains(PixelCohortReport.INTENSITY_OUTLIER)) {
            return "review staining/acquisition or normalise — the whole image is brighter or dimmer than the cohort.";
        }
        return "review — pixel statistics differ from the cohort.";
    }

    private static String verdict(List<String> flags) {
        if (flags.isEmpty()) {
            return PixelCohortReport.VERDICT_OK;
        }
        if (flags.contains(PixelCohortReport.BACKGROUND_HEAVY)) {
            return "Background-heavy";
        }
        if (flags.contains(PixelCohortReport.SATURATED)) {
            return "Saturated";
        }
        if (flags.contains(PixelCohortReport.WEAK_SIGNAL)) {
            return "Weak signal";
        }
        if (flags.contains(PixelCohortReport.INTENSITY_OUTLIER)) {
            return "Intensity outlier";
        }
        return PixelCohortReport.VERDICT_OK;
    }

    // ── Robust statistics helpers ────────────────────────────────────────────────

    /** Find a channel's stats by name within one image, or {@code null}. */
    private static ImagePixelStats.ChannelStats channelOf(ImagePixelStats.ImageStats img, String channel) {
        for (var cs : img.channels()) {
            if (channel.equals(cs.channel())) {
                return cs;
            }
        }
        return null;
    }

    /**
     * Robust z-scores, NaN-preserving, MAD-scaled, with a mean/std fallback for a
     * degenerate MAD. Delegates to {@link RobustStats#robustZWithFallback(double[])};
     * kept here as a package-private seam so existing call sites and tests are
     * unaffected. See that method for the full rationale.
     */
    static double[] robustZ(double[] values) {
        return RobustStats.robustZWithFallback(values);
    }

    private static double medianIgnoreNaN(double[] values) {
        return RobustStats.medianIgnoreNaN(values);
    }

    private static double medianOf(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.NaN;
        }
        double[] arr = new double[values.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = values.get(i);
        }
        return medianIgnoreNaN(arr);
    }

    private static double mean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return Double.NaN;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }

    private static double relu(double x) {
        return (Double.isNaN(x) || x < 0.0) ? 0.0 : x;
    }

    private static boolean ge(double x, double threshold) {
        return !Double.isNaN(x) && x >= threshold;
    }

    private static boolean le(double x, double threshold) {
        return !Double.isNaN(x) && x <= threshold;
    }

    private static boolean notable(double z) {
        return !Double.isNaN(z) && Math.abs(z) >= 2.0;
    }

    // ── Formatting ────────────────────────────────────────────────────────────────

    private static String fmtPct(double frac) {
        if (Double.isNaN(frac)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.1f%%", frac * 100.0);
    }

    private static String fmtZ(double z) {
        if (Double.isNaN(z)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%+.1f", z);
    }

    private static String fmtRatio(double v) {
        if (Double.isNaN(v)) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.2f×", v);
    }

    private static String fmtNum(double v) {
        if (Double.isNaN(v)) {
            return "n/a";
        }
        if (Math.abs(v) >= 1000.0) {
            return String.format(Locale.ROOT, "%.0f", v);
        }
        return String.format(Locale.ROOT, "%.2f", v);
    }
}
