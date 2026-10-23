package qupath.ext.spclassify.classifier;

import java.util.Locale;

/**
 * The best hyperparameters an auto-tune run found for one boosted model type, saved with the
 * classifier state so they can be shown later and reused by a run that does not tune.
 *
 * @param modelType  {@link ModelType} name (XGBOOST or LIGHTGBM)
 * @param numRounds  boosting rounds; the early-stopping result when early stopping held them fixed
 * @param maxDepth   max tree depth
 * @param eta        learning rate
 * @param subsample  row subsample fraction
 * @param colsample  column subsample fraction
 * @param cvMacroF1  mean cross-validated macro F1 of these settings
 * @param trials     TPE trials run
 * @param folds      CV folds requested
 * @param cells      labelled cells the search ran on (real cells, before balancing)
 * @param features   feature count at the time
 * @param classes    class count at the time
 * @param timestamp  when the search ran ({@code yyyyMMdd_HHmmss})
 */
public record TunedParams(
        String modelType,
        int numRounds,
        int maxDepth,
        float eta,
        float subsample,
        float colsample,
        double cvMacroF1,
        int trials,
        int folds,
        int cells,
        int features,
        int classes,
        String timestamp) {

    /** One-line summary of the settings and where they came from, for logs and tooltips. */
    public String describe() {
        return String.format(
                Locale.ROOT,
                "%s: %d rounds, depth %d, eta %.4f, subsample %.2f, colsample %.2f"
                        + " (CV macro F1 %.4f; %d trials x %d folds on %d cells, %d features, %d classes; %s)",
                displayName(),
                numRounds,
                maxDepth,
                eta,
                subsample,
                colsample,
                cvMacroF1,
                trials,
                folds,
                cells,
                features,
                classes,
                timestamp);
    }

    private String displayName() {
        try {
            return ModelType.valueOf(modelType).toString();
        } catch (IllegalArgumentException | NullPointerException ex) {
            return String.valueOf(modelType);
        }
    }
}
