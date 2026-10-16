package qupath.ext.spclassify.classifier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RF probabilities are vote fractions, not SMILE's add-one-smoothed leaf averages. */
class RandomForestProbabilityTest {

    @Test
    void separableClassesGetConfidentProbabilities() throws Exception {
        int k = 10, per = 30, n = k * per, nf = 2;
        float[] x = new float[n * nf];
        float[] y = new float[n];
        java.util.Random rng = new java.util.Random(1);
        for (int i = 0; i < n; i++) {
            y[i] = i / per;
            x[i * nf] = y[i] * 10 + (float) rng.nextGaussian();
            x[i * nf + 1] = y[i] * 10 + (float) rng.nextGaussian();
        }
        List<String> classes = new ArrayList<>();
        for (int c = 0; c < k; c++) classes.add("c" + c);

        var rf = new RandomForestModel();
        rf.train(x, y, n, nf, classes, List.of("f0", "f1"), 100, 100, 0.1f, 0.8f);
        float[][] p = rf.predictProba(new float[] {50f, 50f}, 1, nf); // centre of class 5

        double sum = 0;
        for (float v : p[0]) sum += v;
        assertEquals(1.0, sum, 1e-6);
        // The smoothed soft prediction gave about 0.5 here; vote fractions are near 1.
        assertTrue(p[0][5] > 0.9f, "class 5 probability " + p[0][5]);
    }
}
