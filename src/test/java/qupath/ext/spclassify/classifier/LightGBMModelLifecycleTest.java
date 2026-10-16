package qupath.ext.spclassify.classifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.metarank.lightgbm4j.LGBMBooster;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Booster replacement: a failed retrain keeps the working model; success replaces it; close releases it. */
class LightGBMModelLifecycleTest {

    private static boolean nativesAvailable = false;

    @BeforeAll
    static void loadNatives() {
        try {
            LGBMBooster.loadNative();
            nativesAvailable = LGBMBooster.isNativeLoaded();
        } catch (Throwable t) {
            nativesAvailable = false;
        }
    }

    private static final int N = 60, F = 2;
    private static final List<String> FEATURES = List.of("f0", "f1");

    /** Two separable classes on f0. */
    private static float[] data() {
        float[] x = new float[N * F];
        for (int i = 0; i < N; i++) {
            x[i * F] = i < N / 2 ? 0f : 10f;
            x[i * F + 1] = i % 7;
        }
        return x;
    }

    private static float[] labels(int nClasses) {
        float[] y = new float[N];
        for (int i = 0; i < N; i++) y[i] = (i < N / 2 ? 0 : 1) % nClasses;
        return y;
    }

    private static LightGBMModel trained() throws Exception {
        var m = new LightGBMModel();
        m.train(data(), labels(2), N, F, List.of("A", "B"), FEATURES, 20, 3, 0.1f, 1f, 1f);
        return m;
    }

    @Test
    void failedRetrainKeepsThePreviousModel() throws Exception {
        assumeTrue(nativesAvailable, "LightGBM natives unavailable");
        var m = trained();
        float[][] before = m.predictProba(new float[] {10f, 0f}, 1, F);

        // Label 5 is outside num_class=3, which LightGBM rejects.
        float[] bad = labels(2);
        bad[0] = 5f;
        assertThrows(
                Exception.class,
                () -> m.train(data(), bad, N, F, List.of("A", "B", "C"), FEATURES, 20, 3, 0.1f, 1f, 1f));

        assertTrue(m.isTrained());
        assertEquals(List.of("A", "B"), m.getClassNames(), "class list not changed by the failed run");
        assertArrayEquals(before[0], m.predictProba(new float[] {10f, 0f}, 1, F)[0], 0f);
    }

    @Test
    void retrainReplacesAndCloseReleases() throws Exception {
        assumeTrue(nativesAvailable, "LightGBM natives unavailable");
        var m = trained();
        m.train(data(), labels(2), N, F, List.of("X", "Y"), FEATURES, 20, 3, 0.1f, 1f, 1f);
        assertEquals(List.of("X", "Y"), m.getClassNames());
        assertTrue(m.predictProba(new float[] {10f, 0f}, 1, F)[0][1] > 0.5f);

        byte[] bytes = m.toBytes();
        m.loadFromBytes(bytes, List.of("A", "B"), FEATURES);
        assertEquals(List.of("A", "B"), m.getClassNames());
        assertTrue(m.isTrained());

        m.close();
        assertFalse(m.isTrained());
        m.close(); // second close is a no-op
    }
}
