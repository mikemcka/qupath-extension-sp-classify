package qupath.ext.spclassify;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class ImportExportColumnsTest {

    @Test
    void columnOnlyInAnotherImageIsListed() {
        // Open image has no CD163 cells; another image does.
        var open = List.of("CD3: Cell: Mean", "Distance to detection Tcell µm");
        var other = List.of("CD3: Cell: Mean", "Distance to detection CD163+ macrophage µm");
        var cols = ImportExport.unionColumns(List.of(open, other), List.of(List.of(), List.of()));
        assertEquals(
                List.of(
                        "CD3: Cell: Mean",
                        "Distance to detection Tcell µm",
                        "Distance to detection CD163+ macrophage µm"),
                cols);
    }

    @Test
    void numericBeforeMetadataAndNoDuplicates() {
        var cols = ImportExport.unionColumns(
                List.of(List.of("A"), List.of("B", "A")), List.of(List.of("CN Class"), List.of("CN Class", "B")));
        assertEquals(List.of("A", "B", "CN Class"), cols);
    }

    @Test
    void singleImageUnchanged() {
        var cols = ImportExport.unionColumns(List.of(List.of("X", "Y")), List.of(List.of("M")));
        assertEquals(List.of("X", "Y", "M"), cols);
    }
}
