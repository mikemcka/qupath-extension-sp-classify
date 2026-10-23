package qupath.ext.spclassify.classifier;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SettingsLineTest {

    @Test
    void boostedLineListsEverySettingAndItsSource() {
        String line = DualModelClassifier.settingsLine(
                "Model 1", ModelType.XGBOOST, 127, 4, 0.0731f, 0.75f, 0.6f, "last tuned 20261023_150000", true);
        assertEquals(
                "Model 1 XGBoost: 127 rounds (early stopping), depth 4, eta 0.0731, subsample 0.75, colsample 0.60"
                        + " (last tuned 20261023_150000).",
                line);
    }

    @Test
    void forestLineHasTreesAndDepthOnly() {
        assertEquals(
                "Model 2 Random Forest: 100 trees, max depth 100.",
                DualModelClassifier.settingsLine(
                        "Model 2", ModelType.RANDOM_FOREST, 100, 100, 0.1f, 0.8f, 0.8f, "defaults", true));
    }

    @Test
    void describeIncludesScoreAndSearchSize() {
        var p = new TunedParams("LIGHTGBM", 140, 7, 0.042f, 0.9f, 0.8f, 0.8871, 20, 5, 812, 40, 6, "20261023_150000");
        assertEquals(
                "LightGBM: 140 rounds, depth 7, eta 0.0420, subsample 0.90, colsample 0.80"
                        + " (CV macro F1 0.8871; 20 trials x 5 folds on 812 cells, 40 features, 6 classes;"
                        + " 20261023_150000)",
                p.describe());
    }
}
