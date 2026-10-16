package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchShiftsFingerprintTest {

    private static BatchShifts shifts(Map<String, double[]> scales) {
        var s = new BatchShifts();
        s.mode = "PER_IMAGE";
        s.markers = List.of("CD3: Cell: Mean", "CD8: Cell: Mean");
        s.scaleByImage = scales;
        return s;
    }

    @Test
    void sameGainsGiveSameFingerprintRegardlessOfOrder() {
        Map<String, double[]> a = new LinkedHashMap<>();
        a.put("img1", new double[] {1.0, 2.0});
        a.put("img2", new double[] {0.5, 1.5});
        Map<String, double[]> b = new LinkedHashMap<>();
        b.put("img2", new double[] {0.5, 1.5});
        b.put("img1", new double[] {1.0, 2.0});

        assertEquals(shifts(a).fingerprint(), shifts(b).fingerprint());
    }

    @Test
    void changedGainChangesFingerprint() {
        Map<String, double[]> a = new LinkedHashMap<>();
        a.put("img1", new double[] {1.0, 2.0});
        Map<String, double[]> b = new LinkedHashMap<>();
        b.put("img1", new double[] {1.0, 2.01});

        assertNotEquals(shifts(a).fingerprint(), shifts(b).fingerprint());
    }
}
