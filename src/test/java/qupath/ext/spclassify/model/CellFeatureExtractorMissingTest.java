package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

class CellFeatureExtractorMissingTest {

    private static PathObject cell(String... names) {
        var c = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()));
        for (String n : names) c.getMeasurementList().put(n, 1.0);
        return c;
    }

    @Test
    void reportsNamesNoCellHas() {
        List<PathObject> cells = new ArrayList<>();
        for (int i = 0; i < 10; i++) cells.add(cell("CD3", "CD8"));

        assertEquals(List.of("FOXP3"), CellFeatureExtractor.missingFeatures(cells, List.of("CD3", "FOXP3", "CD8")));
        assertEquals(List.of(), CellFeatureExtractor.missingFeatures(cells, List.of("CD3", "CD8")));
    }

    @Test
    void presentOnSomeCellsIsNotMissing() {
        List<PathObject> cells = List.of(cell("CD3"), cell("CD3", "CD8"));
        assertEquals(List.of(), CellFeatureExtractor.missingFeatures(cells, List.of("CD3", "CD8")));
    }

    @Test
    void summariseNamesTruncates() {
        assertEquals("a, b (+2 more)", CellFeatureExtractor.summariseNames(List.of("a", "b", "c", "d"), 2));
        assertEquals("a, b", CellFeatureExtractor.summariseNames(List.of("a", "b"), 5));
    }
}
