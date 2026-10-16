package qupath.ext.spclassify.classifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Builds the folds early stopping uses to choose the number of boosting rounds.
 * <p>
 * The folds come only from the training 80% of the real labelled cells, so the reported 20%
 * validation set never influences the round count. Below {@link #CV_BELOW} labelled cells the
 * round count is chosen by {@link #CV_FOLDS}-fold cross-validation over the 80% (every training
 * cell is used for choosing, which keeps the choice steady when labels are few); above it a single
 * inner split holding out {@link #INNER_FRACTION} of the 80% (10% of all cells) is enough and costs
 * one search instead of five.
 * <p>
 * Each fold's training part is balanced with the run's resampling strategy on its own, and its
 * validation part stays real. Validation cells are weighted by {@code 1 / (class count)} so each
 * class contributes equally to the validation loss, like macro F1, while the loss stays smooth.
 */
final class RoundSelector {

    /** Labelled cells below which rounds are chosen by cross-validation. */
    static final int CV_BELOW = 2000;
    /** Folds for the cross-validated choice. */
    static final int CV_FOLDS = 5;
    /** Share of the training 80% held out by the single inner split (1/8 of 80% = 10% of all). */
    static final double INNER_FRACTION = 0.125;

    private RoundSelector() {}

    /**
     * One early-stopping fold, flattened and ready for a booster.
     *
     * @param valWeights per validation row, class-balancing weights with mean 1
     */
    record Fold(
            float[] trainData,
            float[] trainLabels,
            int trainSize,
            float[] valData,
            float[] valLabels,
            float[] valWeights,
            int valSize) {}

    /**
     * @param folds       the folds to search over (1 for the inner split)
     * @param description one line for the log and the metrics report
     */
    record Plan(List<Fold> folds, String description) {}

    /**
     * @param rows        all real labelled rows of the run
     * @param labels      their class indices
     * @param trainIdx    indices into {@code rows} of the training 80% (the metrics split's train side)
     * @param nTotal      number of real labelled cells in the run (decides CV vs inner split)
     * @param strategy    resampling applied to each fold's training part
     */
    static Plan plan(
            List<float[]> rows,
            List<Integer> labels,
            int[] trainIdx,
            int nTotal,
            int nClasses,
            int nFeatures,
            ResamplingStrategy strategy) {
        int[] trainLabels = new int[trainIdx.length];
        for (int i = 0; i < trainIdx.length; i++) trainLabels[i] = labels.get(trainIdx[i]);

        List<int[][]> splits;
        String description;
        if (nTotal < CV_BELOW) {
            splits = HyperparameterTuner.stratifiedKFold(trainLabels, nClasses, CV_FOLDS, new Random(42));
            description = "Round count chosen by " + CV_FOLDS + "-fold cross-validation on the training 80%"
                    + " (class-weighted log-loss, patience 20). The validation set was not used.";
        } else {
            splits = java.util.Collections.singletonList(
                    TrainValMetricsComputer.stratifiedSplit(trainLabels, nClasses, 1 - INNER_FRACTION, new Random(42)));
            description = "Round count chosen on a 10% inner split of the training 80%"
                    + " (class-weighted log-loss, patience 20). The validation set was not used.";
        }

        List<Fold> folds = new ArrayList<>(splits.size());
        for (int[][] split : splits) {
            if (split[1].length == 0 || split[0].length == 0) continue;
            folds.add(buildFold(rows, labels, trainIdx, split, nClasses, nFeatures, strategy));
        }
        return new Plan(folds, description);
    }

    private static Fold buildFold(
            List<float[]> rows,
            List<Integer> labels,
            int[] trainIdx,
            int[][] split,
            int nClasses,
            int nFeatures,
            ResamplingStrategy strategy) {
        List<float[]> trRows = new ArrayList<>(split[0].length);
        List<Integer> trLabels = new ArrayList<>(split[0].length);
        for (int j : split[0]) {
            trRows.add(rows.get(trainIdx[j]));
            trLabels.add(labels.get(trainIdx[j]));
        }
        // Silent: once per fold, and its per-class narration would bury the search lines.
        Consumer<String> quiet = s -> {};
        Resampler.Result res = Resampler.apply(trRows, trLabels, nClasses, strategy, quiet);
        int m = res.size();
        float[][] rr = res.rowArray();
        int[] rl = res.labelArray();
        float[] trainData = new float[m * nFeatures];
        float[] trainLabelArr = new float[m];
        for (int i = 0; i < m; i++) {
            System.arraycopy(rr[i], 0, trainData, i * nFeatures, nFeatures);
            trainLabelArr[i] = rl[i];
        }

        int v = split[1].length;
        float[] valData = new float[v * nFeatures];
        float[] valLabels = new float[v];
        for (int i = 0; i < v; i++) {
            int row = trainIdx[split[1][i]];
            System.arraycopy(rows.get(row), 0, valData, i * nFeatures, nFeatures);
            valLabels[i] = labels.get(row);
        }
        return new Fold(trainData, trainLabelArr, m, valData, valLabels, classBalancedWeights(valLabels, nClasses), v);
    }

    /**
     * Weight {@code n / (classesPresent × count(class))} per row: every class present gets the same
     * total weight, and the weights average 1 so the loss stays on the usual scale.
     */
    static float[] classBalancedWeights(float[] labels, int nClasses) {
        int[] count = new int[nClasses];
        for (float l : labels) count[(int) l]++;
        int present = 0;
        for (int c : count) if (c > 0) present++;
        float[] w = new float[labels.length];
        for (int i = 0; i < labels.length; i++) {
            w[i] = (float) labels.length / (present * count[(int) labels[i]]);
        }
        return w;
    }
}
