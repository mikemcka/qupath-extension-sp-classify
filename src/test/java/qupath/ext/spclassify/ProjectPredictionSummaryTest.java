package qupath.ext.spclassify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.spclassify.model.CellPrediction;
import qupath.ext.spclassify.model.PopulationSet;

class ProjectPredictionSummaryTest {

    private static final List<String> CLASSES = List.of("Other", "Tcell");

    private static PopulationSet predictions(List<String> classes) {
        PopulationSet set = new PopulationSet("Pred_ALL");
        float[] p = new float[classes.size()];
        p[0] = 1f;
        set.put("a", new CellPrediction("a", classes.get(0), classes.get(0), p, p, classes));
        set.put("b", new CellPrediction("b", classes.get(0), classes.get(0), p, p, classes));
        return set;
    }

    @Test
    void countsPredictionsMadeWithTheCurrentClassList() {
        var row = ProjectPredictionSummary.inputRow("img", predictions(CLASSES), CLASSES);

        assertEquals(2, row.predictedCells());
        assertNotNull(row.avgCounts());
        assertEquals(2L, row.avgCounts().get("Other"));
    }

    @Test
    void differentClassListGetsNoCountsAndANote() {
        // e.g. predicted before Other_immune_cells was merged into Other
        var row = ProjectPredictionSummary.inputRow(
                "img", predictions(List.of("Other", "Other_immune_cells", "Tcell")), CLASSES);

        assertEquals(0, row.predictedCells(), "excluded from the cohort anomaly scoring");
        assertNull(row.avgCounts());
        assertEquals(ProjectPredictionSummary.DIFFERENT_CLASSES, row.classCountsText());
    }

    @Test
    void noClassifierSkipsTheCheck() {
        var row = ProjectPredictionSummary.inputRow("img", predictions(List.of("CD3+", "CD3-")), null);

        assertEquals(2, row.predictedCells());
    }

    @Test
    void missingPredictionsSayNoSavedPredictions() {
        var row = ProjectPredictionSummary.inputRow("img", null, CLASSES);

        assertEquals(0, row.predictedCells());
        assertEquals(ProjectPredictionSummary.NO_PREDICTIONS, row.classCountsText());
    }
}
