package qupath.ext.spclassify.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.model.PixelPrescreenCache;
import qupath.lib.projects.Project;

/**
 * Persistence for image pixel prescreen results
 * ({@code <project>/celltune/pixel-prescreen.json}). Mirrors {@link BatchNormPersistence}:
 * package-private, and the public API on {@link ProjectStateManager} delegates here.
 * <p>
 * Uses its own Gson because the stats contain {@code NaN} (saturation of float images,
 * focus of tiny images), which the shared instance refuses to write.
 */
final class PixelPrescreenPersistence {

    private static final Logger logger = LoggerFactory.getLogger(PixelPrescreenPersistence.class);
    static final String FILENAME = "pixel-prescreen.json";

    // Not pretty-printed: a 60-channel image is ~30 KB pretty-printed.
    private static final Gson GSON =
            new GsonBuilder().serializeSpecialFloatingPointValues().create();

    private PixelPrescreenPersistence() {} // utility class

    static void save(Project<?> project, PixelPrescreenCache cache) throws IOException {
        if (project == null || cache == null) {
            return;
        }
        Path path = ProjectStateManager.getCellTuneDir(project).resolve(FILENAME);
        write(path, cache);
        logger.info(
                "Saved pixel prescreen results ({} images) to {}",
                cache.images().size(),
                path);
    }

    /** @return the saved results, or {@code null} if none / unreadable. */
    static PixelPrescreenCache load(Project<?> project) {
        Path dir = ProjectStateManager.cellTuneDirPath(project);
        return dir == null ? null : read(dir.resolve(FILENAME));
    }

    /** Write via a temporary file so an interrupted save cannot leave a truncated file. */
    static void write(Path path, PixelPrescreenCache cache) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(cache), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static PixelPrescreenCache read(Path path) {
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), PixelPrescreenCache.class);
        } catch (Exception e) {
            logger.warn("Failed to load pixel prescreen results from {}: {}", path, e.getMessage());
            return null;
        }
    }
}
