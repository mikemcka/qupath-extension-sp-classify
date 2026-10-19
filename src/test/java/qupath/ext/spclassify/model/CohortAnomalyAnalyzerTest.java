package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class CohortAnomalyAnalyzerTest {

    @Test
    void detectsRareTypeEnrichmentUsingBaselineAndCountGates() {
        var inputs = List.of(
                input("img-rare", 1000, 120, counts("NK", 60, "T", 940)),
                input("img-b", 5000, 40, counts("T", 5000)),
                input("img-c", 5000, 35, counts("T", 5000)));

        var report = CohortAnomalyAnalyzer.analyze(inputs);
        var byImage = report.byImageName();
        var rare = byImage.get("img-rare");

        assertNotNull(rare);
        assertTrue(rare.flagReasons().contains(CohortAnomalyReport.RARE_ENRICHMENT));
        assertTrue(rare.highlightedClasses().contains("NK"));

        var nk = rare.enrichmentByClass().get("NK");
        assertNotNull(nk);
        assertTrue(nk.rareCandidate());
        assertTrue(nk.highlighted());
        assertTrue(nk.enrichmentFold() >= CohortAnomalyReport.DEFAULT_THRESHOLDS.rareEnrichmentFoldMin());
        assertTrue(nk.count() >= CohortAnomalyReport.DEFAULT_THRESHOLDS.rareMinCount());
    }

    @Test
    void ranksDisagreementAndCompositionOutlierAtTop() {
        var inputs = List.of(
                input("img-1", 1000, 40, counts("A", 500, "B", 500)),
                input("img-2", 1000, 45, counts("A", 510, "B", 490)),
                input("img-3", 1000, 48, counts("A", 495, "B", 505)),
                input("img-4", 1000, 42, counts("A", 505, "B", 495)),
                input("img-5", 1000, 39, counts("A", 498, "B", 502)),
                input("img-6", 1000, 41, counts("A", 503, "B", 497)),
                input("img-outlier", 1000, 700, counts("A", 980, "B", 20)));

        var report = CohortAnomalyAnalyzer.analyze(inputs);
        assertFalse(report.images().isEmpty());

        var top = report.images().get(0);
        assertEquals("img-outlier", top.imageName());
        assertTrue(top.flagged());
        assertTrue(top.anomalyScore() > 0.0);
        assertTrue(top.flagReasons().contains(CohortAnomalyReport.OUTLIER_DISAGREEMENT)
                || top.flagReasons().contains(CohortAnomalyReport.OUTLIER_COMPOSITION));
    }

    @Test
    void keepsBalancedCohortUnflagged() {
        var inputs = List.of(
                input("img-a", 1000, 50, counts("A", 500, "B", 500)),
                input("img-b", 1000, 50, counts("A", 500, "B", 500)),
                input("img-c", 1000, 50, counts("A", 500, "B", 500)),
                input("img-d", 1000, 50, counts("A", 500, "B", 500)));

        var report = CohortAnomalyAnalyzer.analyze(inputs);
        assertEquals(4, report.images().size());

        for (var row : report.images()) {
            assertFalse(row.flagged());
            assertEquals(0.0, row.anomalyScore(), 1e-9);
            assertTrue(row.flagReasons().isEmpty());
        }
    }

    @Test
    void patchyRareClassFlagsOnlyTheImageFarAboveOtherImages() {
        // NK is under 1% of all cells but patchy: 0, 5, 10, 20 or 40 cells per 4000-cell image.
        // Under the old rule (>= 3x the project average, >= 20 cells) the 20- and 40-cell
        // images were all flagged; only the 400-cell image is unusual among images.
        var inputs = new java.util.ArrayList<CohortAnomalyReport.ImageInput>();
        int[] nk = {0, 0, 5, 5, 10, 10, 10, 20, 20, 20, 40, 40};
        for (int i = 0; i < nk.length; i++) {
            inputs.add(input("img-" + i, 4000, 200, counts("NK", nk[i], "T", 4000 - nk[i])));
        }
        inputs.add(input("img-hot", 4000, 200, counts("NK", 400, "T", 3600)));
        int[] patchy = {0, 5, 10, 20, 40};
        for (int i = 0; i < 40; i++) {
            int c = patchy[i % patchy.length];
            inputs.add(input("bulk-" + i, 4000, 200, counts("NK", c, "T", 4000 - c)));
        }

        var byImage = CohortAnomalyAnalyzer.analyze(inputs).byImageName();
        assertTrue(byImage.get("img-hot").highlightedClasses().contains("NK"));
        for (int i = 0; i < nk.length; i++) {
            assertFalse(
                    byImage.get("img-" + i).flagReasons().contains(CohortAnomalyReport.RARE_ENRICHMENT),
                    "img-" + i + " "
                            + byImage.get("img-" + i).enrichmentByClass().get("NK"));
        }
        var hot = byImage.get("img-hot").enrichmentByClass().get("NK");
        assertTrue(hot.outlierZ() >= 3.0);
        assertTrue(hot.foldVsTypical() > 10.0);
    }

    @Test
    void identicalFractionsNeedAtLeastTheMinimumFold() {
        // Every image has the same NK fraction (MAD 0): the floor means 2.5x is not
        // enough and 4x is.
        var inputs = new java.util.ArrayList<CohortAnomalyReport.ImageInput>();
        for (int i = 0; i < 10; i++) {
            inputs.add(input("img-" + i, 10_000, 500, counts("NK", 20, "T", 9980)));
        }
        inputs.add(input("x2.5", 10_000, 500, counts("NK", 50, "T", 9950)));
        inputs.add(input("x4", 10_000, 500, counts("NK", 80, "T", 9920)));

        var byImage = CohortAnomalyAnalyzer.analyze(inputs).byImageName();
        assertFalse(byImage.get("x2.5").highlightedClasses().contains("NK"));
        assertTrue(byImage.get("x4").highlightedClasses().contains("NK"));
        assertFalse(byImage.get("img-0").highlightedClasses().contains("NK"));
    }

    @Test
    void highDisagreementNeedsEnoughCells() {
        var inputs = new java.util.ArrayList<CohortAnomalyReport.ImageInput>();
        for (int i = 0; i < 10; i++) {
            inputs.add(input("img-" + i, 5000, 400 + i * 10, counts("A", 2500, "B", 2500)));
        }
        // Same 40% disagreement: noise for 30 cells, a real problem for 5000.
        inputs.add(input("small", 30, 12, counts("A", 15, "B", 15)));
        inputs.add(input("large", 5000, 2000, counts("A", 2500, "B", 2500)));

        var byImage = CohortAnomalyAnalyzer.analyze(inputs).byImageName();
        var small = byImage.get("small");
        assertTrue(small.disagreementRobustZ() >= 3.0);
        assertFalse(small.flagReasons().contains(CohortAnomalyReport.OUTLIER_DISAGREEMENT));
        assertTrue(small.anomalyScore() > 0.0, "z still counts in the score");
        assertTrue(byImage.get("large").flagReasons().contains(CohortAnomalyReport.OUTLIER_DISAGREEMENT));
    }

    private static CohortAnomalyReport.ImageInput input(
            String imageName, long predicted, long disagreements, LinkedHashMap<String, Long> classCounts) {
        return new CohortAnomalyReport.ImageInput(imageName, predicted, disagreements, classCounts);
    }

    private static LinkedHashMap<String, Long> counts(Object... kv) {
        var out = new LinkedHashMap<String, Long>();
        for (int i = 0; i < kv.length; i += 2) {
            out.put(String.valueOf(kv[i]), ((Number) kv[i + 1]).longValue());
        }
        return out;
    }
}
