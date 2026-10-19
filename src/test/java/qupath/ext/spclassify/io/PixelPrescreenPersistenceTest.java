package qupath.ext.spclassify.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.spclassify.model.ImagePixelStats;
import qupath.ext.spclassify.model.PixelPrescreenCache;

class PixelPrescreenPersistenceTest {

    @TempDir
    Path tmp;

    @Test
    void roundTripKeepsValuesIncludingNaN() throws Exception {
        // Float images have NaN saturation; a 2x2 image has NaN focus.
        var ch = ImagePixelStats.computeChannel("CD3", new float[] {1, 5, 9, 200}, Double.NaN, 2, 2);
        assertTrue(Double.isNaN(ch.saturationFraction()));
        assertTrue(Double.isNaN(ch.laplacianVariance()));
        var tissue = new ImagePixelStats.TissueProfile("DAPI", 3, new double[] {0.0, 1.5, Double.NaN, 7.25});
        var stats = new ImagePixelStats.ImageStats("img", 4.0, 2, 2, List.of(ch), 0.25, "entry-1", List.of(tissue));
        var cache = PixelPrescreenCache.empty()
                .merge(List.of(stats), "2026-10-19T00:00:00Z")
                .withTissueChannel("DAPI");

        Path file = tmp.resolve(PixelPrescreenPersistence.FILENAME);
        PixelPrescreenPersistence.write(file, cache);
        var back = PixelPrescreenPersistence.read(file);

        assertEquals(cache, back);
        assertEquals("DAPI", back.tissueChannel());
        assertEquals(tissue, back.images().get(0).tissueProfile("DAPI"));
        assertTrue(back.isCurrent());
        assertFalse(Files.exists(tmp.resolve(PixelPrescreenPersistence.FILENAME + ".tmp")));
    }

    @Test
    void missingOrCorruptFileReadsAsNull() throws Exception {
        Path file = tmp.resolve(PixelPrescreenPersistence.FILENAME);
        assertNull(PixelPrescreenPersistence.read(file));
        Files.writeString(file, "{ not json");
        assertNull(PixelPrescreenPersistence.read(file));
    }
}
