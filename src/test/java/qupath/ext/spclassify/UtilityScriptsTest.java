package qupath.ext.spclassify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The Filter Cells measurement pre-selection (the dialog itself needs JavaFX and is not tested). */
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
}
