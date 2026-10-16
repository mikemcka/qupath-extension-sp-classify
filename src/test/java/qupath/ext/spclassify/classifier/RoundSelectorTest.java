package qupath.ext.spclassify.classifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.metarank.lightgbm4j.LGBMBooster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Early-stopping folds come from the training 80% only; the lockstep search matches the old one on one fold. */
class RoundSelectorTest {

    private static final int N_CLASSES = 4, N_FEATURES = 12;

    /** Rows whose first feature encodes the row index, so tests can tell which rows a fold used. */
    private static List<float[]> rows(int n) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            float[] r = new float[N_FEATURES];
            r[0] = i;
            out.add(r);
        }
        return out;
    }

    private static List<Integer> labels(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(i % N_CLASSES);
        return out;
    }

    /** Row ids (feature 0) present in a flattened matrix. */
    private static List<Integer> ids(float[] data, int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add((int) data[i * N_FEATURES]);
        return out;
    }

    @Test
    void fewLabelsUseFiveFoldCvOverTheTrainingSideOnly() {
        int n = 100;
        int[] trainIdx = java.util.stream.IntStream.range(0, 80).toArray(); // rows 80-99 = reported 20%
        var plan = RoundSelector.plan(rows(n), labels(n), trainIdx, n, N_CLASSES, N_FEATURES, ResamplingStrategy.NONE);

        assertEquals(RoundSelector.CV_FOLDS, plan.folds().size());
        assertTrue(plan.description().contains("5-fold cross-validation"));
        List<Integer> allVal = new ArrayList<>();
        for (var f : plan.folds()) {
            for (int id : ids(f.trainData(), f.trainSize())) assertTrue(id < 80, "train row from the 20%: " + id);
            for (int id : ids(f.valData(), f.valSize())) assertTrue(id < 80, "val row from the 20%: " + id);
            allVal.addAll(ids(f.valData(), f.valSize()));
        }
        allVal.sort(null);
        assertEquals(
                java.util.stream.IntStream.range(0, 80).boxed().toList(), allVal, "each training cell validated once");
    }

    @Test
    void manyLabelsUseOneInnerSplitOfAboutTenPercent() {
        int n = RoundSelector.CV_BELOW;
        int[] trainIdx = java.util.stream.IntStream.range(0, 1600).toArray();
        var plan = RoundSelector.plan(rows(n), labels(n), trainIdx, n, N_CLASSES, N_FEATURES, ResamplingStrategy.NONE);

        assertEquals(1, plan.folds().size());
        var f = plan.folds().get(0);
        assertEquals(200, f.valSize(), "1/8 of the 80% = 10% of all cells");
        for (int id : ids(f.valData(), f.valSize())) assertTrue(id < 1600);
    }

    @Test
    void classWeightsGiveEveryClassTheSameTotalAndAverageOne() {
        float[] y = {0, 0, 0, 0, 0, 0, 1, 1, 2};
        float[] w = RoundSelector.classBalancedWeights(y, 4);

        double sum = 0, c0 = 0, c1 = 0, c2 = 0;
        for (int i = 0; i < y.length; i++) {
            sum += w[i];
            if (y[i] == 0) c0 += w[i];
            if (y[i] == 1) c1 += w[i];
            if (y[i] == 2) c2 += w[i];
        }
        assertEquals(1.0, sum / y.length, 1e-6);
        assertEquals(c0, c1, 1e-5);
        assertEquals(c1, c2, 1e-5);
    }

    // ── one fold with unit weights reproduces the previous single-split search ──

    private static void fill(float[] data, float[] labels, int rows, int salt) {
        for (int i = 0; i < rows; i++) {
            int cls = i % N_CLASSES;
            labels[i] = cls;
            for (int f = 0; f < N_FEATURES; f++) {
                float base = (((i + salt) * 31L + f * 7L) % 997) / 997f;
                data[i * N_FEATURES + f] = f < 4 ? base + cls * 0.6f : base;
            }
        }
    }

    private static RoundSelector.Fold unitFold() {
        int tr = 240, va = 80;
        float[] td = new float[tr * N_FEATURES],
                tl = new float[tr],
                vd = new float[va * N_FEATURES],
                vl = new float[va];
        fill(td, tl, tr, 0);
        fill(vd, vl, va, 613);
        float[] w = new float[va];
        Arrays.fill(w, 1f);
        return new RoundSelector.Fold(td, tl, tr, vd, vl, w, va);
    }

    @Test
    void xgboostSingleFoldMatchesThePreviousSearch() throws Exception {
        try {
            new ml.dmlc.xgboost4j.java.DMatrix(new float[] {0f, 1f}, 1, 2, Float.NaN).dispose();
        } catch (Throwable t) {
            assumeTrue(false, "XGBoost natives unavailable");
        }
        var f = unitFold();
        int old = XGBoostModel.findBestRounds(
                f.trainData(),
                f.trainLabels(),
                f.trainSize(),
                f.valData(),
                f.valLabels(),
                f.valSize(),
                N_FEATURES,
                N_CLASSES,
                300,
                4,
                0.1f,
                0.8f,
                20,
                s -> {});
        int now = XGBoostModel.cvBestRounds(List.of(f), N_FEATURES, N_CLASSES, 300, 4, 0.1f, 0.8f, 20, s -> {});
        assertEquals(old, now);
    }

    @Test
    void lightgbmSingleFoldMatchesThePreviousSearch() throws Exception {
        boolean natives;
        try {
            LGBMBooster.loadNative();
            natives = LGBMBooster.isNativeLoaded();
        } catch (Throwable t) {
            natives = false;
        }
        assumeTrue(natives, "LightGBM natives unavailable");
        var f = unitFold();
        int old = LightGBMModel.findBestRounds(
                f.trainData(),
                f.trainLabels(),
                f.trainSize(),
                f.valData(),
                f.valLabels(),
                f.valSize(),
                N_FEATURES,
                N_CLASSES,
                300,
                4,
                0.05f,
                0.8f,
                20,
                s -> {});
        int now = LightGBMModel.cvBestRounds(List.of(f), N_FEATURES, N_CLASSES, 300, 4, 0.05f, 0.8f, 20, s -> {});
        assertEquals(old, now);
    }
}
