package qupath.ext.spclassify.ui;

import qupath.ext.spclassify.classifier.ModelType;

/**
 * Labels for the two model slots of the dual classifier, built from the model types actually
 * selected rather than assuming the default XGBoost + LightGBM pair. A {@code null} type (no
 * classifier yet, or state saved before model types were recorded) falls back to that default.
 */
public final class ModelLabels {

    private ModelLabels() {} // utility class

    /** The slot's type, or the default pair's type for that slot when {@code type} is null. */
    public static ModelType orDefault(int slot, ModelType type) {
        if (type != null) return type;
        return slot == 2 ? ModelType.LIGHTGBM : ModelType.XGBOOST;
    }

    /** "Model 1 (XGBoost)", "Model 2 (Random Forest)", … */
    public static String full(int slot, ModelType type) {
        return "Model " + slot + " (" + orDefault(slot, type).getDisplayName() + ")";
    }

    /**
     * Compact prefix for a review button: "XGB", "LGB", "RF" — or "RF 1" / "RF 2" when both
     * slots use the same type, so the two buttons stay distinguishable.
     */
    public static String shortPrefix(int slot, ModelType type, ModelType otherSlotType) {
        ModelType t = orDefault(slot, type);
        ModelType other = orDefault(slot == 1 ? 2 : 1, otherSlotType);
        return t == other ? t.getShortName() + " " + slot : t.getShortName();
    }
}
