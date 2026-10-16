package qupath.ext.spclassify.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.spclassify.classifier.TrainingMetrics;

class TrainingMetricsViewTest {

    private static final List<String> CLASSES = List.of("A", "B");

    /** Perfectly predicted metrics over {@code n} rows, half per class. */
    private static TrainingMetrics metrics(int n, int realCount) {
        float[] labels = new float[n];
        float[][] proba = new float[n][2];
        for (int i = 0; i < n; i++) {
            labels[i] = i % 2;
            proba[i][i % 2] = 1f;
        }
        return TrainingMetrics.compute("m", CLASSES, labels, proba, realCount);
    }

    @Test
    void headerCountsRealCellsNotResampledRows() {
        // 2635 real train cells resampled to 7608 rows, 669 val cells.
        String header = TrainingMetricsView.header(metrics(7608, 2635), metrics(669, 669), false);

        assertTrue(header.contains("3304 labelled cells (80% train = 2635, 20% val = 669)"), header);
        assertTrue(header.contains("to 7608 rows"), header);
        assertFalse(header.contains("8277"), header);
    }

    @Test
    void headerHasNoBalancingLineWithoutResampling() {
        String header = TrainingMetricsView.header(metrics(80, 80), metrics(20, 20), true);

        assertTrue(header.contains("100 labelled cells (80% train = 80, 20% val = 20)"), header);
        assertFalse(header.contains("Balancing"), header);
        assertFalse(header.contains("synthetic"), header);
    }

    @Test
    void trainRowsNoteMentionsSyntheticRowsWhenResampled() {
        String header = TrainingMetricsView.header(metrics(300, 100), metrics(25, 25), true);

        assertTrue(header.contains("synthetic rows"), header);
    }

    @Test
    void headerFromOlderSavedMetricsShowsOnlyValidationCount() {
        // realCount 0 = metrics saved before the field existed.
        String header = TrainingMetricsView.header(metrics(7608, 0), metrics(669, 0), false);

        assertTrue(header.contains("Validation set: 669 labelled cells."), header);
        assertFalse(header.contains("Training data used"), header);
    }

    @Test
    void headerShowsHowTheRoundCountWasChosen() {
        String header = TrainingMetricsView.header(
                metrics(80, 80),
                metrics(20, 20).withRoundSelection("Round count chosen by 5-fold cross-validation."),
                false);
        assertTrue(header.contains("Round count chosen by 5-fold cross-validation."), header);
        assertFalse(header.contains("earlier version"), header);
    }

    @Test
    void headerFlagsMetricsFromEarlierVersions() {
        String header = TrainingMetricsView.header(metrics(80, 80), metrics(20, 20), false);
        assertTrue(header.contains("earlier version"), header);
    }
}
