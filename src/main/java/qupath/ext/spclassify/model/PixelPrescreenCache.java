package qupath.ext.spclassify.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Saved image pixel prescreen results for one project: the raw per-image
 * {@link ImagePixelStats.ImageStats}, keyed by project entry ID.
 * <p>
 * Only the raw statistics are stored. The cohort report (z-scores, flags, verdicts)
 * is recomputed from them by {@link PixelCohortAnalyzer} each time it is shown, so
 * it always reflects the images currently in the project and the current rules.
 * <p>
 * Results are reused only when {@link #isCurrent()}: they were computed with the
 * current statistic definitions ({@link ImagePixelStats#STATS_VERSION}) and read
 * at the current resolution ({@link ImagePixelStatsReader#DEFAULT_MAX_LONG_EDGE}).
 *
 * @param statsVersion {@link ImagePixelStats#STATS_VERSION} when the stats were computed
 * @param maxLongEdge  long-edge target in pixels the images were read at
 * @param scannedAt    ISO-8601 time of the most recent scan
 * @param images       per-image statistics; each has a non-null {@code imageId}
 */
public record PixelPrescreenCache(
        int statsVersion, int maxLongEdge, String scannedAt, List<ImagePixelStats.ImageStats> images) {

    public PixelPrescreenCache {
        var kept = new ArrayList<ImagePixelStats.ImageStats>();
        if (images != null) {
            for (var s : images) {
                if (s != null && s.imageId() != null) {
                    kept.add(s);
                }
            }
        }
        images = List.copyOf(kept);
    }

    /** @return an empty cache for the current statistic definitions and read size. */
    public static PixelPrescreenCache empty() {
        return new PixelPrescreenCache(
                ImagePixelStats.STATS_VERSION, ImagePixelStatsReader.DEFAULT_MAX_LONG_EDGE, null, List.of());
    }

    /** @return true if the saved stats can be compared with stats computed now. */
    public boolean isCurrent() {
        return statsVersion == ImagePixelStats.STATS_VERSION
                && maxLongEdge == ImagePixelStatsReader.DEFAULT_MAX_LONG_EDGE;
    }

    /**
     * Add newly scanned images, replacing any saved stats with the same entry ID.
     *
     * @param scanned   newly computed stats (entries without an ID are ignored)
     * @param timestamp ISO-8601 time of this scan
     * @return a new cache
     */
    public PixelPrescreenCache merge(Collection<ImagePixelStats.ImageStats> scanned, String timestamp) {
        var byId = new LinkedHashMap<String, ImagePixelStats.ImageStats>();
        for (var s : images) {
            byId.put(s.imageId(), s);
        }
        if (scanned != null) {
            for (var s : scanned) {
                if (s != null && s.imageId() != null) {
                    byId.put(s.imageId(), s);
                }
            }
        }
        return new PixelPrescreenCache(statsVersion, maxLongEdge, timestamp, new ArrayList<>(byId.values()));
    }

    /**
     * Stats for the images that are still in the project, in project order, named
     * with their current project names. Saved stats for removed images are left out.
     *
     * @param projectImages entry ID → current image name, in project order
     * @return the matching stats
     */
    public List<ImagePixelStats.ImageStats> forProject(Map<String, String> projectImages) {
        var byId = new LinkedHashMap<String, ImagePixelStats.ImageStats>();
        for (var s : images) {
            byId.put(s.imageId(), s);
        }
        var out = new ArrayList<ImagePixelStats.ImageStats>();
        for (var e : projectImages.entrySet()) {
            var s = byId.get(e.getKey());
            if (s != null) {
                out.add(s.withIdentity(e.getValue(), e.getKey()));
            }
        }
        return out;
    }

    /**
     * @param projectIds entry IDs of the images in the project
     * @return the IDs with no saved stats, in the given order
     */
    public List<String> missingIds(Collection<String> projectIds) {
        var have = new java.util.HashSet<String>();
        for (var s : images) {
            have.add(s.imageId());
        }
        var out = new ArrayList<String>();
        for (String id : projectIds) {
            if (!have.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
