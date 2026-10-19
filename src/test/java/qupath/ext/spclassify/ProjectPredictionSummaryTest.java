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
        var row = ProjectPredictionSummary.inputRow("img", (PopulationSet) null, CLASSES);

        assertEquals(0, row.predictedCells());
        assertEquals(ProjectPredictionSummary.NO_PREDICTIONS, row.classCountsText());
    }

    @Test
    void oneUnreadableImageDoesNotStopTheOthers() throws Exception {
        var names = List.of("a", "bad", "oom", "d");
        var done = new java.util.concurrent.atomic.AtomicInteger();
        var rows = ProjectPredictionSummary.loadInputRows(
                names,
                name -> switch (name) {
                    case "bad" -> throw new com.google.gson.JsonSyntaxException("truncated");
                    case "oom" -> throw new OutOfMemoryError("simulated");
                    default -> ProjectPredictionSummary.inputRow(name, predictions(CLASSES), CLASSES);
                },
                done::incrementAndGet);

        assertEquals(
                names,
                rows.stream()
                        .map(ProjectPredictionSummary.SummaryInputRow::imageName)
                        .toList());
        assertEquals(4, done.get());
        assertEquals(2, rows.get(0).predictedCells());
        assertEquals(ProjectPredictionSummary.READ_ERROR, rows.get(1).classCountsText());
        assertEquals(ProjectPredictionSummary.READ_ERROR, rows.get(2).classCountsText());
        assertEquals(2, rows.get(3).predictedCells());
    }

    @Test
    void agreementIsNumericAndFormattedForDisplay() {
        var rows = ProjectPredictionSummary.buildRows(List.of(
                ProjectPredictionSummary.inputRow("x", predictions(CLASSES), CLASSES),
                ProjectPredictionSummary.inputRow("none", (PopulationSet) null, CLASSES)));
        assertEquals(100.0, rows.get(0).agreementPercent(), 1e-9);
        assertEquals("100.0%", rows.get(0).agreementText());
        assertTrue(Double.isNaN(rows.get(1).agreementPercent()));
        assertEquals("-", rows.get(1).agreementText());
    }
}
