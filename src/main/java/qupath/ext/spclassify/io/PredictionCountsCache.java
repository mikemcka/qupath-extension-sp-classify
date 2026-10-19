package qupath.ext.spclassify.io;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.model.PredictionCounts;
import qupath.lib.projects.Project;

/**
 * Saved {@link PredictionCounts} per image ({@code <project>/celltune/prediction-summary-cache.json}),
 * so the Project Prediction Summary does not re-read every predictions file each time it opens.
 * <p>
 * An entry is reused only while its predictions file has the same size and modification
 * time as when it was counted; otherwise the file is counted again. Thread-safe:
 * {@link #get} may be called from several workers, then {@link #save} once.
 */
public final class PredictionCountsCache {

    private static final Logger logger = LoggerFactory.getLogger(PredictionCountsCache.class);
    static final String FILENAME = "prediction-summary-cache.json";
    private static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    /** Saved form of one image's counts. */
    static final class Entry {
        long size;
        long modified;
        List<String> classNames;
        long cells;
        long disagreements;
        LinkedHashMap<String, Long> avgCounts;
    }

    private static final class Saved {
        int version;
        Map<String, Entry> entries;
    }

    private final Project<?> project;
    private final Path file;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private volatile boolean changed;

    private PredictionCountsCache(Project<?> project, Path file) {
        this.project = project;
        this.file = file;
    }

    /** Load the saved counts of {@code project} (an empty cache if none or unreadable). */
    public static PredictionCountsCache open(Project<?> project) {
        Path dir = ProjectStateManager.cellTuneDirPath(project);
        var cache = new PredictionCountsCache(project, dir == null ? null : dir.resolve(FILENAME));
        if (cache.file != null && Files.exists(cache.file)) {
            try {
                Saved saved = GSON.fromJson(Files.readString(cache.file, StandardCharsets.UTF_8), Saved.class);
                if (saved != null && saved.version == VERSION && saved.entries != null) {
                    saved.entries.forEach((k, v) -> {
                        if (k != null && v != null) {
                            cache.entries.put(k, v);
                        }
                    });
                }
            } catch (Exception e) {
                logger.warn("Ignoring unreadable prediction summary cache {}: {}", cache.file, e.getMessage());
            }
        }
        return cache;
    }

    /**
     * Counts for one image's saved predictions: the saved entry if the file is unchanged,
     * otherwise counted from the file now.
     *
     * @param scope     binary marker, or {@code null} for multi-class
     * @param imageName image name
     * @return the counts, or {@code null} when the image has no valid saved predictions
     * @throws IOException if the predictions file cannot be read
     */
    public PredictionCounts get(String scope, String imageName) throws IOException {
        Path predictions = PredictionPersistence.imagePredictionsFile(project, scope, imageName);
        String key = key(scope, imageName);
        if (!Files.exists(predictions)) {
            if (entries.remove(key) != null) {
                changed = true;
            }
            return null;
        }
        long size = Files.size(predictions);
        long modified = Files.getLastModifiedTime(predictions).toMillis();
        Entry e = entries.get(key);
        if (e != null && e.size == size && e.modified == modified) {
            return e.cells == 0 ? null : new PredictionCounts(e.classNames, e.cells, e.disagreements, e.avgCounts);
        }
        PredictionCounts counts = PredictionPersistence.countPredictionsFile(predictions);
        Entry fresh = new Entry();
        fresh.size = size;
        fresh.modified = modified;
        if (counts != null) {
            fresh.classNames = counts.classNames();
            fresh.cells = counts.cells();
            fresh.disagreements = counts.disagreements();
            fresh.avgCounts = new LinkedHashMap<>(counts.avgCounts());
        }
        entries.put(key, fresh);
        changed = true;
        return counts;
    }

    /** Write the cache if anything changed. Failures are logged, not thrown. */
    public void save() {
        if (!changed || file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Saved saved = new Saved();
            saved.version = VERSION;
            saved.entries = new java.util.TreeMap<>(entries);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(saved), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            changed = false;
        } catch (IOException e) {
            logger.warn("Could not save prediction summary cache {}: {}", file, e.getMessage());
        }
    }

    private static String key(String scope, String imageName) {
        String s = (scope == null || scope.isBlank()) ? "" : ProjectStateManager.sanitiseFileName(scope);
        return s + "/" + ProjectStateManager.sanitiseFileName(imageName);
    }
}
