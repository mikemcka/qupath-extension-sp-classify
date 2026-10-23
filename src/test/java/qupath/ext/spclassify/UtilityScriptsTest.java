package qupath.ext.spclassify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

/**
 * The Filter Cells measurement pre-selection and the Delete Measurements by Keyword matching (the
 * dialogs themselves need JavaFX and are not tested).
 */
class UtilityScriptsTest {

    private static final List<String> QUPATH_CELL = List.of(
            "Nucleus: Area µm^2",
            "Nucleus: Circularity",
            "Cell: Area px^2",
            "Cell: Area µm^2",
            "Cell: Circularity",
            "Cytoplasm: Area µm^2",
            "Nucleus/Cell area ratio");

    @Test
    void measurementsContainingIsCaseInsensitiveAndKeepsOrder() {
        assertEquals(
                List.of(
                        "Nucleus: Area µm^2",
                        "Cell: Area px^2",
                        "Cell: Area µm^2",
                        "Cytoplasm: Area µm^2",
                        "Nucleus/Cell area ratio"),
                UtilityScripts.measurementsContaining(QUPATH_CELL, "AREA"));
    }

    @Test
    void wholeCellAreaInMicronsIsPreferredOverTheFirstMatch() {
        var area = UtilityScripts.measurementsContaining(QUPATH_CELL, "area");
        // The first match is the nucleus area — the old behaviour silently used it.
        assertEquals("Nucleus: Area µm^2", area.get(0));
        assertEquals("Cell: Area µm^2", UtilityScripts.preferredMeasurement(area, "area"));
        var circ = UtilityScripts.measurementsContaining(QUPATH_CELL, "circularity");
        assertEquals("Cell: Circularity", UtilityScripts.preferredMeasurement(circ, "circularity"));
    }

    @Test
    void pixelAreaIsUsedWhenTheImageIsUncalibrated() {
        assertEquals(
                "Cell: Area px^2",
                UtilityScripts.preferredMeasurement(List.of("Nucleus: Area px^2", "Cell: Area px^2"), "area"));
    }

    @Test
    void plainDetectionNamesAndCompartmentFallbacks() {
        assertEquals("Area µm^2", UtilityScripts.preferredMeasurement(List.of("Nucleus: Area", "Area µm^2"), "area"));
        assertEquals("Nucleus: Area µm^2", UtilityScripts.preferredMeasurement(List.of("Nucleus: Area µm^2"), "area"));
        assertNull(UtilityScripts.preferredMeasurement(List.of(), "area"));
    }

    private static PathObject cell(String... measurements) {
        PathObject cell =
                PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 1, 1, ImagePlane.getDefaultPlane()));
        for (String m : measurements) cell.getMeasurementList().put(m, 1.0);
        return cell;
    }

    @Test
    void keywordMatchesColumnsThatOnlySomeCellsHave() {
        // "Distance to other <class>" is written only on cells of that class.
        var tCell = cell("CD3: Mean", "Distance to detection B µm", "Distance to other T µm");
        var bCell = cell("CD3: Mean", "Distance to detection T µm", "Distance to other B µm");
        var cells = List.of(tCell, bCell);

        assertEquals(
                List.of(
                        "Distance to detection B µm",
                        "Distance to other T µm",
                        "Distance to detection T µm",
                        "Distance to other B µm"),
                UtilityScripts.matchingMeasurementNames(cells, "distance", false));
        assertEquals(List.of(), UtilityScripts.matchingMeasurementNames(cells, "distance", true));

        assertEquals(2, UtilityScripts.removeMeasurementsByKeyword(cells, "Distance", true));
        assertEquals(List.of("CD3: Mean"), tCell.getMeasurementList().getNames());
        assertEquals(List.of("CD3: Mean"), bCell.getMeasurementList().getNames());
    }
}
