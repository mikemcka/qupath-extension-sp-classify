package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class PixelPrescreenCacheTest {

    private static ImagePixelStats.ImageStats stats(String name, String id, double empty) {
        var ch = ImagePixelStats.computeChannel("DAPI", new float[] {1, 2, 3, 4}, 255.0);
        return new ImagePixelStats.ImageStats(name, 1.0, 2, 2, List.of(ch), empty, id);
    }

    @Test
    void mergeReplacesByIdAndKeepsOthers() {
        var cache = PixelPrescreenCache.empty().merge(List.of(stats("a", "1", 0.1), stats("b", "2", 0.2)), "t1");
        var merged = cache.merge(List.of(stats("b", "2", 0.9), stats("c", "3", 0.3)), "t2");

        assertEquals("t2", merged.scannedAt());
        assertEquals(3, merged.images().size());
        var byId = new LinkedHashMap<String, Double>();
        merged.images().forEach(s -> byId.put(s.imageId(), s.emptyFraction()));
        assertEquals(0.1, byId.get("1"));
        assertEquals(0.9, byId.get("2"));
        assertEquals(0.3, byId.get("3"));
    }

    @Test
    void statsWithoutIdAreDropped() {
        var cache = PixelPrescreenCache.empty().merge(List.of(stats("a", null, 0.1), stats("b", "2", 0.2)), "t");
        assertEquals(1, cache.images().size());
        assertEquals("2", cache.images().get(0).imageId());
    }

    @Test
    void forProjectUsesProjectOrderCurrentNamesAndSkipsRemovedImages() {
        var cache = PixelPrescreenCache.empty()
                .merge(List.of(stats("old-a", "1", 0.1), stats("b", "2", 0.2), stats("gone", "9", 0.5)), "t");
        var project = new LinkedHashMap<String, String>();
        project.put("2", "b");
        project.put("1", "renamed-a");
        project.put("4", "not-scanned");

        var out = cache.forProject(project);

        assertEquals(2, out.size());
        assertEquals("2", out.get(0).imageId());
        assertEquals("1", out.get(1).imageId());
        assertEquals("renamed-a", out.get(1).imageName());
        assertEquals(List.of("4"), cache.missingIds(project.keySet()));
    }

    @Test
    void isCurrentChecksStatsVersionAndReadSize() {
        assertTrue(PixelPrescreenCache.empty().isCurrent());
        assertFalse(new PixelPrescreenCache(
                        ImagePixelStats.STATS_VERSION - 1, ImagePixelStatsReader.DEFAULT_MAX_LONG_EDGE, null, List.of())
                .isCurrent());
        assertFalse(new PixelPrescreenCache(ImagePixelStats.STATS_VERSION, 1024, null, List.of()).isCurrent());
    }

    @Test
    void nullImageListBecomesEmpty() {
        var cache = new PixelPrescreenCache(1, 2048, null, null);
        assertTrue(cache.images().isEmpty());
    }

    @Test
    void analyzerCarriesImageIdIntoReport() {
        var inputs = new ArrayList<ImagePixelStats.ImageStats>();
        for (int i = 0; i < 6; i++) {
            inputs.add(stats("img" + i, "id" + i, 0.1 + 0.01 * i));
        }
        var report = PixelCohortAnalyzer.analyze(inputs);
        var byName = report.byImageName();
        for (int i = 0; i < 6; i++) {
            assertEquals("id" + i, byName.get("img" + i).imageId());
        }
    }

    @Test
    void withIdentityKeepsStats() {
        var s = stats("a", "1", 0.4);
        var t = s.withIdentity("b", "2");
        assertEquals("b", t.imageName());
        assertEquals("2", t.imageId());
        assertEquals(0.4, t.emptyFraction());
        assertSame(s.channels().get(0), t.channels().get(0));
    }
}
