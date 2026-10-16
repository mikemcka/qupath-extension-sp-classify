package qupath.ext.spclassify.classifier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import qupath.ext.spclassify.model.CellPrediction;
import qupath.ext.spclassify.model.PopulationSet;

class UncertaintySamplerTest {

    private static final List<String> CLASSES = List.of("A", "B");

    @Test
    void neverSamplesReviewedCellsIncludingPerImageTier() {
        // 40 A/B disagreements on one image; 36 already labelled, 4 not.
        PopulationSet preds = new PopulationSet("Pred_ALL");
        Map<String, String> cellToImage = new HashMap<>();
        Set<String> reviewed = new HashSet<>();
        for (int i = 0; i < 40; i++) {
            String id = "cell-" + i;
            preds.put(
                    id, new CellPrediction(id, "A", "B", new float[] {0.6f, 0.4f}, new float[] {0.4f, 0.6f}, CLASSES));
            cellToImage.put(id, "img");
            if (i >= 4) reviewed.add(id);
        }

        List<String> sampled = UncertaintySampler.sample(
                preds, CLASSES, null, 256, List.of(), List.of(), cellToImage, reviewed, new Random(1));

        assertEquals(Set.of("cell-0", "cell-1", "cell-2", "cell-3"), new HashSet<>(sampled));
        assertEquals(4, sampled.size(), "no cell sampled twice");
    }
}
