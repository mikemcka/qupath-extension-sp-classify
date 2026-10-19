package qupath.ext.spclassify.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary counts of one image's saved {@code Pred_ALL} predictions: what the Project
 * Prediction Summary needs, without the per-cell probabilities.
 * <p>
 * Equal to computing {@link PopulationSet#size()}, {@link PopulationSet#getDisagreementCount()}
 * and {@link PopulationSet#getAvgCounts()} on the loaded predictions.
 *
 * @param classNames    the class list the predictions were made with
 * @param cells         number of cells with a prediction
 * @param disagreements cells where the two models predicted different classes
 * @param avgCounts     cells per averaged-probability class, in order of first appearance
 */
public record PredictionCounts(List<String> classNames, long cells, long disagreements, Map<String, Long> avgCounts) {

    public PredictionCounts {
        classNames = classNames == null ? List.of() : List.copyOf(classNames);
        avgCounts = avgCounts == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(avgCounts));
    }

    /** @return the counts of loaded predictions, or {@code null} for null/empty input */
    public static PredictionCounts of(PopulationSet predictions) {
        if (predictions == null || predictions.size() == 0) {
            return null;
        }
        var first = predictions.getAll().values().iterator().next();
        return new PredictionCounts(
                first.getClassNames(),
                predictions.size(),
                predictions.getDisagreementCount(),
                predictions.getAvgCounts());
    }
}
