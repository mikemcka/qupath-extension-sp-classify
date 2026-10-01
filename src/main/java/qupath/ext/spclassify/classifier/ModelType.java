package qupath.ext.spclassify.classifier;

/**
 * Available model types for the dual-model classifier.
 * Any combination of two different types can be used for the disagreement paradigm.
 */
public enum ModelType {
    XGBOOST("XGBoost", "XGB"),
    LIGHTGBM("LightGBM", "LGB"),
    RANDOM_FOREST("Random Forest", "RF");

    private final String displayName;
    private final String shortName;

    ModelType(String displayName, String shortName) {
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Abbreviation for compact UI labels (review buttons): XGB, LGB, RF. */
    public String getShortName() {
        return shortName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
