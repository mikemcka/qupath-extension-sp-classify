package qupath.ext.spclassify.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import qupath.ext.spclassify.util.RobustStats;

/**
 * Cohort-level scoring for rare-type enrichment and image anomaly detection.
 * <p>
 * <b>RARE_ENRICHMENT.</b> A class that is at most {@code rareBaselineCutoff} of all
 * project cells is "rare". For each rare class, every image's class fraction is put on
 * a log scale, {@code log((count + 0.5) / (cells + 1))}, and compared across images with
 * a robust z. The flag needs that z ≥ {@code rareOutlierZ} and ≥ {@code rareMinCount}
 * cells. Rare classes are patchy (absent from many images, clustered in others), so
 * "N× the project average" — the previous rule — fired on a quarter of the images of a
 * real cohort; comparing with the spread across images does not. The MAD is floored at
 * {@code 0.6745 · ln(rareEnrichmentFoldMin) / rareOutlierZ}, so that when most images
 * have the same fraction (e.g. none) the flag still needs at least
 * {@code rareEnrichmentFoldMin}× the median image's fraction, and never fires on noise.
 * <p>
 * <b>HIGH_DISAGREEMENT</b> is not set for images with fewer than
 * {@code disagreementMinCells} predicted cells: with 20–50 cells a typical image reaches
 * a disagreement z of +4 to +9 by chance. Their z still counts in the anomaly score.
 */
public final class CohortAnomalyAnalyzer {

    private CohortAnomalyAnalyzer() {}

    public static CohortAnomalyReport analyze(List<CohortAnomalyReport.ImageInput> inputs) {
        return analyze(inputs, CohortAnomalyReport.DEFAULT_THRESHOLDS);
    }

    public static CohortAnomalyReport analyze(
            List<CohortAnomalyReport.ImageInput> inputs, CohortAnomalyReport.Thresholds thresholds) {

        if (thresholds == null) {
            thresholds = CohortAnomalyReport.DEFAULT_THRESHOLDS;
        }

        var safeInputs = inputs == null ? List.<CohortAnomalyReport.ImageInput>of() : List.copyOf(inputs);
        if (safeInputs.isEmpty()) {
            return new CohortAnomalyReport(List.of());
        }

        Set<String> classNames = new LinkedHashSet<>();
        for (var input : safeInputs) {
            if (input == null || input.classCounts() == null) {
                continue;
            }
            classNames.addAll(input.classCounts().keySet());
        }

        if (classNames.isEmpty()) {
            var rows = new ArrayList<CohortAnomalyReport.ImageAnomaly>(safeInputs.size());
            for (var input : safeInputs) {
                if (input == null) {
                    continue;
                }
                rows.add(new CohortAnomalyReport.ImageAnomaly(
                        input.imageName(),
                        0.0,
                        false,
                        List.of(),
                        Map.of(),
                        0.0,
                        disagreementRate(input),
                        0.0,
                        0.0,
                        List.of()));
            }
            rows.sort(Comparator.comparing(CohortAnomalyReport.ImageAnomaly::imageName));
            return new CohortAnomalyReport(rows);
        }

        var orderedClasses = classNames.stream().sorted().toList();
        var baselineFractions = computeBaselineFractions(safeInputs, orderedClasses, thresholds);

        var rareStats = rareClassOutliers(safeInputs, orderedClasses, baselineFractions, thresholds);

        var rows = new ArrayList<Intermediate>(safeInputs.size());
        var compositionValues = new double[safeInputs.size()];
        var predictedCells = new long[safeInputs.size()];
        var disagreementValues = new double[safeInputs.size()];

        int idx = 0;
        for (var input : safeInputs) {
            if (input == null) {
                continue;
            }

            var enrichments = new LinkedHashMap<String, CohortAnomalyReport.ClassEnrichment>(orderedClasses.size());
            var imageFractions = imageFractions(input.classCounts(), orderedClasses, thresholds.smoothingAlpha());
            var highlightedClasses = new ArrayList<String>();

            for (String className : orderedClasses) {
                long count = input.classCounts().getOrDefault(className, 0L);
                double baseline = baselineFractions.getOrDefault(className, 0.0);
                double image = imageFractions.getOrDefault(className, 0.0);
                double fold = baseline > 0.0 ? image / baseline : 0.0;
                RareStats rs = rareStats.get(className);
                boolean rareCandidate = rs != null;
                double z = rareCandidate ? rs.z()[idx] : Double.NaN;
                double foldVsTypical = rareCandidate ? rs.foldVsTypical()[idx] : Double.NaN;
                boolean highlighted =
                        rareCandidate && count >= thresholds.rareMinCount() && z >= thresholds.rareOutlierZ();
                if (highlighted) {
                    highlightedClasses.add(className);
                }
                enrichments.put(
                        className,
                        new CohortAnomalyReport.ClassEnrichment(
                                className, count, baseline, image, fold, rareCandidate, highlighted, foldVsTypical, z));
            }

            double compositionDistance = jensenShannonDistance(imageFractions, baselineFractions, orderedClasses);
            double disagreementRate = disagreementRate(input);
            compositionValues[idx] = compositionDistance;
            disagreementValues[idx] = disagreementRate;
            predictedCells[idx] = input.predictedCells();

            rows.add(new Intermediate(
                    input.imageName(), enrichments, highlightedClasses, compositionDistance, disagreementRate));
            idx++;
        }

        compositionValues = Arrays.copyOf(compositionValues, idx);
        disagreementValues = Arrays.copyOf(disagreementValues, idx);

        var compositionZ = robustZScores(compositionValues);
        var disagreementZ = robustZScores(disagreementValues);

        var output = new ArrayList<CohortAnomalyReport.ImageAnomaly>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            double compositionPos = Math.max(0.0, compositionZ[i]);
            double disagreementPos = Math.max(0.0, disagreementZ[i]);
            double anomalyScore =
                    thresholds.compositionWeight() * compositionPos + thresholds.disagreementWeight() * disagreementPos;

            var reasons = new ArrayList<String>(3);
            if (!row.highlightedClasses.isEmpty()) {
                reasons.add(CohortAnomalyReport.RARE_ENRICHMENT);
            }
            if (compositionZ[i] >= thresholds.robustZOutlierThreshold()) {
                reasons.add(CohortAnomalyReport.OUTLIER_COMPOSITION);
            }
            if (disagreementZ[i] >= thresholds.robustZOutlierThreshold()
                    && predictedCells[i] >= thresholds.disagreementMinCells()) {
                reasons.add(CohortAnomalyReport.OUTLIER_DISAGREEMENT);
            }

            output.add(new CohortAnomalyReport.ImageAnomaly(
                    row.imageName,
                    anomalyScore,
                    !reasons.isEmpty(),
                    reasons,
                    row.enrichmentByClass,
                    row.compositionDistance,
                    row.disagreementRate,
                    compositionZ[i],
                    disagreementZ[i],
                    row.highlightedClasses));
        }

        output.sort(Comparator.comparingDouble(CohortAnomalyReport.ImageAnomaly::anomalyScore)
                .reversed()
                .thenComparing(Comparator.comparingDouble(CohortAnomalyReport.ImageAnomaly::disagreementRate)
                        .reversed())
                .thenComparing(CohortAnomalyReport.ImageAnomaly::imageName));

        return new CohortAnomalyReport(output);
    }

    /** Per rare class: robust z of each image's log class fraction, and its fold vs the median image. */
    private record RareStats(double[] z, double[] foldVsTypical) {}

    /**
     * Robust z of {@code log((count + 0.5) / (cells + 1))} across images, for every rare
     * class, with the MAD floored so that z ≥ {@code rareOutlierZ} implies at least
     * {@code rareEnrichmentFoldMin}× the median image's fraction. Indexed like the
     * non-null entries of {@code inputs}.
     */
    private static Map<String, RareStats> rareClassOutliers(
            List<CohortAnomalyReport.ImageInput> inputs,
            List<String> orderedClasses,
            Map<String, Double> baselineFractions,
            CohortAnomalyReport.Thresholds thresholds) {
        var nonNull = inputs.stream().filter(java.util.Objects::nonNull).toList();
        double madFloor = RobustStats.MAD_TO_SIGMA
                * Math.log(Math.max(1.0, thresholds.rareEnrichmentFoldMin()))
                / thresholds.rareOutlierZ();
        var out = new LinkedHashMap<String, RareStats>();
        for (String className : orderedClasses) {
            if (baselineFractions.getOrDefault(className, 0.0) > thresholds.rareBaselineCutoff()) {
                continue;
            }
            double[] logFraction = new double[nonNull.size()];
            for (int i = 0; i < nonNull.size(); i++) {
                var in = nonNull.get(i);
                long count = in.classCounts().getOrDefault(className, 0L);
                long total = 0L;
                for (long c : in.classCounts().values()) {
                    total += c;
                }
                logFraction[i] = Math.log((count + 0.5) / (total + 1.0));
            }
            double median = RobustStats.median(logFraction);
            double[] absDev = new double[logFraction.length];
            for (int i = 0; i < logFraction.length; i++) {
                absDev[i] = Math.abs(logFraction[i] - median);
            }
            double scale = Math.max(RobustStats.median(absDev), madFloor);
            double[] z = new double[logFraction.length];
            double[] fold = new double[logFraction.length];
            for (int i = 0; i < logFraction.length; i++) {
                z[i] = scale > 0 ? RobustStats.MAD_TO_SIGMA * (logFraction[i] - median) / scale : 0.0;
                fold[i] = Math.exp(logFraction[i] - median);
            }
            out.put(className, new RareStats(z, fold));
        }
        return out;
    }

    private static double disagreementRate(CohortAnomalyReport.ImageInput input) {
        long predicted = Math.max(0L, input.predictedCells());
        if (predicted == 0L) {
            return 0.0;
        }
        return Math.min(1.0, Math.max(0.0, (double) input.disagreements() / predicted));
    }

    private static Map<String, Double> computeBaselineFractions(
            List<CohortAnomalyReport.ImageInput> inputs,
            List<String> orderedClasses,
            CohortAnomalyReport.Thresholds thresholds) {

        var totals = new LinkedHashMap<String, Long>(orderedClasses.size());
        for (String className : orderedClasses) {
            totals.put(className, 0L);
        }

        long cohortTotal = 0L;
        for (var input : inputs) {
            if (input == null || input.classCounts() == null) {
                continue;
            }
            for (String className : orderedClasses) {
                long count = input.classCounts().getOrDefault(className, 0L);
                totals.merge(className, count, Long::sum);
                cohortTotal += count;
            }
        }

        double alpha = thresholds.smoothingAlpha();
        double denominator = cohortTotal + alpha * orderedClasses.size();
        var baseline = new LinkedHashMap<String, Double>(orderedClasses.size());
        for (String className : orderedClasses) {
            long count = totals.getOrDefault(className, 0L);
            baseline.put(className, (count + alpha) / denominator);
        }
        return baseline;
    }

    private static Map<String, Double> imageFractions(
            Map<String, Long> classCounts, List<String> orderedClasses, double smoothingAlpha) {

        classCounts = classCounts == null ? Map.of() : classCounts;

        long total = 0L;
        for (String className : orderedClasses) {
            total += classCounts.getOrDefault(className, 0L);
        }

        double denominator = total + smoothingAlpha * orderedClasses.size();
        var fractions = new LinkedHashMap<String, Double>(orderedClasses.size());
        for (String className : orderedClasses) {
            long count = classCounts.getOrDefault(className, 0L);
            fractions.put(className, (count + smoothingAlpha) / denominator);
        }
        return fractions;
    }

    private static double jensenShannonDistance(
            Map<String, Double> image, Map<String, Double> baseline, List<String> orderedClasses) {

        double klImage = 0.0;
        double klBaseline = 0.0;

        for (String className : orderedClasses) {
            double p = image.getOrDefault(className, 0.0);
            double q = baseline.getOrDefault(className, 0.0);
            double m = 0.5 * (p + q);
            if (p > 0.0 && m > 0.0) {
                klImage += p * Math.log(p / m);
            }
            if (q > 0.0 && m > 0.0) {
                klBaseline += q * Math.log(q / m);
            }
        }

        // 0.5·(KL(P‖M)+KL(Q‖M)) is the JS *divergence*; the metric JS *distance*
        // is its square root. The divergence is ≥ 0 by Gibbs' inequality, but clamp
        // first so floating-point round-off can't hand sqrt a tiny negative.
        double divergence = 0.5 * (klImage + klBaseline);
        return Math.sqrt(Math.max(0.0, divergence));
    }

    private static double[] robustZScores(double[] values) {
        // Strict variant: cell-level prediction stats are finite, and a flat
        // distribution (degenerate MAD) legitimately means "no outliers" → zeros.
        return RobustStats.robustZStrict(values);
    }

    private static final class Intermediate {
        private final String imageName;
        private final Map<String, CohortAnomalyReport.ClassEnrichment> enrichmentByClass;
        private final List<String> highlightedClasses;
        private final double compositionDistance;
        private final double disagreementRate;

        private Intermediate(
                String imageName,
                Map<String, CohortAnomalyReport.ClassEnrichment> enrichmentByClass,
                List<String> highlightedClasses,
                double compositionDistance,
                double disagreementRate) {
            this.imageName = imageName == null ? "" : imageName;
            this.enrichmentByClass = enrichmentByClass == null ? Map.of() : Map.copyOf(enrichmentByClass);
            this.highlightedClasses = highlightedClasses == null ? List.of() : List.copyOf(highlightedClasses);
            this.compositionDistance = compositionDistance;
            this.disagreementRate = disagreementRate;
        }
    }
}
