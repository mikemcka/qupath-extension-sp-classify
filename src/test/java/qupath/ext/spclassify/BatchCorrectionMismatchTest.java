package qupath.ext.spclassify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import qupath.ext.spclassify.model.BatchShifts;

class BatchCorrectionMismatchTest {

    private static BatchShifts shifts(double gain) {
        var s = new BatchShifts();
        s.mode = "PER_IMAGE";
        s.markers = List.of("CD3: Cell: Mean");
        s.scaleByImage = Map.of("img", new double[] {gain});
        return s;
    }

    @Test
    void noMessageWhenUnchangedOrUnknown() {
        var s = shifts(1.5);
        assertNull(BatchCorrection.mismatch(true, s.fingerprint(), s));
        assertNull(BatchCorrection.mismatch(false, null, null));
        assertNull(BatchCorrection.mismatch(null, null, s), "model saved before the record existed");
    }

    @Test
    void reportsToggleChange() {
        assertTrue(BatchCorrection.mismatch(true, "x", null).contains("was on"));
        assertTrue(BatchCorrection.mismatch(false, null, shifts(1.5)).contains("was off"));
    }

    @Test
    void reportsRefittedGains() {
        String msg = BatchCorrection.mismatch(true, shifts(1.5).fingerprint(), shifts(1.6));
        assertNotNull(msg);
        assertTrue(msg.contains("re-fitted"));
    }
}
