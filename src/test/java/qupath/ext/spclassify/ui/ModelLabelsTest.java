package qupath.ext.spclassify.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import qupath.ext.spclassify.classifier.ModelType;

class ModelLabelsTest {

    @Test
    void nullTypesFallBackToTheDefaultPair() {
        assertEquals("Model 1 (XGBoost)", ModelLabels.full(1, null));
        assertEquals("Model 2 (LightGBM)", ModelLabels.full(2, null));
        assertEquals("XGB", ModelLabels.shortPrefix(1, null, null));
        assertEquals("LGB", ModelLabels.shortPrefix(2, null, null));
    }

    @Test
    void labelsFollowTheSelectedModelTypes() {
        assertEquals("Model 1 (Random Forest)", ModelLabels.full(1, ModelType.RANDOM_FOREST));
        assertEquals("Model 2 (XGBoost)", ModelLabels.full(2, ModelType.XGBOOST));
        assertEquals("RF", ModelLabels.shortPrefix(1, ModelType.RANDOM_FOREST, ModelType.LIGHTGBM));
        assertEquals("LGB", ModelLabels.shortPrefix(2, ModelType.LIGHTGBM, ModelType.RANDOM_FOREST));
    }

    @Test
    void sameTypeInBothSlotsIsNumberedSoTheButtonsDiffer() {
        assertEquals("RF 1", ModelLabels.shortPrefix(1, ModelType.RANDOM_FOREST, ModelType.RANDOM_FOREST));
        assertEquals("RF 2", ModelLabels.shortPrefix(2, ModelType.RANDOM_FOREST, ModelType.RANDOM_FOREST));
        // A null slot means its default, so XGB in slot 1 next to a null slot 2 (= LightGBM) is unnumbered.
        assertEquals("XGB", ModelLabels.shortPrefix(1, ModelType.XGBOOST, null));
    }
}
