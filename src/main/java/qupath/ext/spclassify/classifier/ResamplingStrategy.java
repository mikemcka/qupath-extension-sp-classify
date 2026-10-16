package qupath.ext.spclassify.classifier;

/**
 * Available resampling strategies for addressing class imbalance
 * in the training data before model fitting.
 */
public enum ResamplingStrategy {
    NONE("None"),
    SMOTE("SMOTE"),
    ADASYN("ADASYN"),
    TOMEK("Tomek links"),
    SMOTE_TOMEK("SMOTE + Tomek"),
    ADASYN_TOMEK("ADASYN + Tomek");

    private final String label;

    ResamplingStrategy(String label) {
        this.label = label;
    }

    /**
     * Fewest labelled cells a class needs for SMOTE/ADASYN to interpolate from its usual
     * 5 nearest neighbours (the cell itself + 5). Classes below this are oversampled from fewer
     * neighbours, down to copies along a single line between two cells.
     */
    public static final int MIN_CELLS_FOR_NEIGHBOURS = 6;

    /** True for the strategies that synthesise cells from same-class neighbours. */
    public boolean usesNeighbours() {
        return this == SMOTE || this == ADASYN || this == SMOTE_TOMEK || this == ADASYN_TOMEK;
    }

    /**
     * "A (3), B (1)" for the classes in {@code counts} with fewer than
     * {@link #MIN_CELLS_FOR_NEIGHBOURS} cells, or an empty string when there are none.
     */
    public static String smallClasses(java.util.Map<String, Integer> counts) {
        var parts = new java.util.ArrayList<String>();
        new java.util.TreeMap<>(counts).forEach((cls, n) -> {
            if (n > 0 && n < MIN_CELLS_FOR_NEIGHBOURS) parts.add(cls + " (" + n + ")");
        });
        return String.join(", ", parts);
    }

    /** Display label for UI combo boxes. */
    @Override
    public String toString() {
        return label;
    }
}
