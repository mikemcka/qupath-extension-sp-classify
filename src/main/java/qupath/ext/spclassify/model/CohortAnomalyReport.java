package qupath.ext.spclassify.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable output contracts for project-level cohort anomaly analysis.
 */
public final class CohortAnomalyReport {

    public static final String RARE_ENRICHMENT = "RARE_ENRICHMENT";
    public static final String OUTLIER_COMPOSITION = "COMPOSITION_OUTLIER";
    public static final String OUTLIER_DISAGREEMENT = "HIGH_DISAGREEMENT";

    public static final Thresholds DEFAULT_THRESHOLDS =
            new Thresholds(0.01, 20L, 3.0, 3.0, 3.0, 200L, 1.0, 0.65, 0.35, 0.5);

    private final List<ImageAnomaly> images;

    public CohortAnomalyReport(List<ImageAnomaly> images) {
        this.images = images == null ? List.of() : List.copyOf(images);
    }

    public List<ImageAnomaly> images() {
        return images;
    }

    public Map<String, ImageAnomaly> byImageName() {
        var byName = new LinkedHashMap<String, ImageAnomaly>(images.size());
        for (var image : images) {
            byName.put(image.imageName(), image);
        }
        return byName;
    }

    /**
     * Cutoffs for the flags and the anomaly score.
     *
     * @param rareBaselineCutoff      a class is "rare" if it is at most this fraction of all project cells
     * @param rareMinCount            RARE_ENRICHMENT needs at least this many cells of the class in the image
     * @param rareEnrichmentFoldMin   RARE_ENRICHMENT needs at least this many times the class fraction of
     *                                the median image (enforced through the MAD floor, see
     *                                {@code CohortAnomalyAnalyzer})
     * @param rareOutlierZ            RARE_ENRICHMENT needs the image's log class fraction to have a robust z
     *                                of at least this across images
     * @param robustZOutlierThreshold COMPOSITION_OUTLIER / HIGH_DISAGREEMENT robust-z cutoff
     * @param disagreementMinCells    HIGH_DISAGREEMENT is not set for images with fewer predicted cells
     *                                (their disagreement rate is too noisy)
     * @param smoothingAlpha          add-alpha smoothing of class fractions for the composition distance
     * @param compositionWeight       anomaly-score weight of the composition z
     * @param disagreementWeight      anomaly-score weight of the disagreement z
     * @param confidenceDisagreementThreshold unused; kept for compatibility
     */
    public record Thresholds(
            double rareBaselineCutoff,
            long rareMinCount,
            double rareEnrichmentFoldMin,
            double rareOutlierZ,
            double robustZOutlierThreshold,
            long disagreementMinCells,
            double smoothingAlpha,
            double compositionWeight,
            double disagreementWeight,
            double confidenceDisagreementThreshold) {

        public Thresholds {
            if (rareBaselineCutoff < 0 || rareBaselineCutoff > 1) {
                throw new IllegalArgumentException("rareBaselineCutoff must be in [0, 1]");
            }
            if (rareMinCount < 1) {
                throw new IllegalArgumentException("rareMinCount must be >= 1");
            }
            if (rareEnrichmentFoldMin < 1.0) {
                throw new IllegalArgumentException("rareEnrichmentFoldMin must be >= 1");
            }
            if (rareOutlierZ <= 0) {
                throw new IllegalArgumentException("rareOutlierZ must be > 0");
            }
            if (disagreementMinCells < 0) {
                throw new IllegalArgumentException("disagreementMinCells must be >= 0");
            }
            if (robustZOutlierThreshold < 0) {
                throw new IllegalArgumentException("robustZOutlierThreshold must be >= 0");
            }
            if (smoothingAlpha <= 0) {
                throw new IllegalArgumentException("smoothingAlpha must be > 0");
            }
            if (compositionWeight < 0 || disagreementWeight < 0) {
                throw new IllegalArgumentException("weights must be >= 0");
            }
            if (Math.abs((compositionWeight + disagreementWeight) - 1.0) > 1e-6) {
                throw new IllegalArgumentException("compositionWeight + disagreementWeight must equal 1");
            }
            if (confidenceDisagreementThreshold < 0 || confidenceDisagreementThreshold > 1) {
                throw new IllegalArgumentException("confidenceDisagreementThreshold must be in [0, 1]");
            }
        }
    }

    public record ImageInput(String imageName, long predictedCells, long disagreements, Map<String, Long> classCounts) {

        public ImageInput {
            imageName = imageName == null ? "" : imageName;
            predictedCells = Math.max(0L, predictedCells);
            disagreements = Math.max(0L, disagreements);
            classCounts = classCounts == null ? Map.of() : Map.copyOf(classCounts);
        }
    }

    /**
     * One class in one image.
     *
     * @param className        class name
     * @param count            cells of the class in the image
     * @param baselineFraction smoothed fraction of the class over all project cells
     * @param imageFraction    smoothed fraction of the class in the image
     * @param enrichmentFold   {@code imageFraction / baselineFraction}
     * @param rareCandidate    the class is rare in the project
     * @param highlighted      RARE_ENRICHMENT fired for this class
     * @param foldVsTypical    the image's class fraction ÷ the median image's ({@code NaN} if not rare)
     * @param outlierZ         robust z of the image's log class fraction across images ({@code NaN} if not rare)
     */
    public record ClassEnrichment(
            String className,
            long count,
            double baselineFraction,
            double imageFraction,
            double enrichmentFold,
            boolean rareCandidate,
            boolean highlighted,
            double foldVsTypical,
            double outlierZ) {}

    public record ImageAnomaly(
            String imageName,
            double anomalyScore,
            boolean flagged,
            List<String> flagReasons,
            Map<String, ClassEnrichment> enrichmentByClass,
            double compositionDistance,
            double disagreementRate,
            double compositionRobustZ,
            double disagreementRobustZ,
            List<String> highlightedClasses) {

        public ImageAnomaly {
            imageName = imageName == null ? "" : imageName;
            anomalyScore = Math.max(0.0, anomalyScore);
            flagReasons = flagReasons == null ? List.of() : List.copyOf(flagReasons);
            enrichmentByClass = enrichmentByClass == null ? Map.of() : Map.copyOf(enrichmentByClass);
            highlightedClasses = highlightedClasses == null ? List.of() : List.copyOf(highlightedClasses);
        }
    }
}
