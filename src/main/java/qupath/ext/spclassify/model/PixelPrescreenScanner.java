package qupath.ext.spclassify.model;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.util.BackgroundExecutors;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Reads project images for the image pixel prescreen. Each image is opened from its
 * server builder only — the saved object hierarchy (which can hold millions of
 * cells) is never loaded.
 */
public final class PixelPrescreenScanner {

    private static final Logger logger = LoggerFactory.getLogger(PixelPrescreenScanner.class);

    /** Images read at the same time. Each read holds one downsampled region with all channels. */
    public static final int MAX_THREADS = 4;

    private PixelPrescreenScanner() {}

    /**
     * Result of a scan.
     *
     * @param stats     stats for the images read successfully, in input order
     * @param failed    names of the images that could not be read
     * @param cancelled true if the scan was cancelled before every image was read
     */
    public record Result(List<ImagePixelStats.ImageStats> stats, List<String> failed, boolean cancelled) {}

    /**
     * Read and analyse each image.
     *
     * @param entries   images to read
     * @param progress  called with the number of images finished so far (from worker threads)
     * @param cancelled polled before each image; images not yet started are skipped once it returns true
     * @return the stats, failures and cancellation state
     */
    public static Result scan(
            List<ProjectImageEntry<BufferedImage>> entries, IntConsumer progress, BooleanSupplier cancelled) {
        int total = entries.size();
        if (total == 0) {
            return new Result(List.of(), List.of(), false);
        }
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = BackgroundExecutors.newFixedPool(Math.min(total, MAX_THREADS), "sp-classify-pixel-scan");
        var futures = new ArrayList<Future<ImagePixelStats.ImageStats>>(total);
        try {
            for (var entry : entries) {
                futures.add(pool.submit(() -> {
                    try {
                        if (cancelled.getAsBoolean()) {
                            return null;
                        }
                        return read(entry);
                    } finally {
                        progress.accept(done.incrementAndGet());
                    }
                }));
            }
            var stats = new ArrayList<ImagePixelStats.ImageStats>(total);
            var failed = new ArrayList<String>();
            boolean wasCancelled = false;
            for (int i = 0; i < total; i++) {
                String name = entries.get(i).getImageName();
                try {
                    var s = futures.get(i).get();
                    if (s != null) {
                        stats.add(s);
                    } else {
                        wasCancelled = true;
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    pool.shutdownNow();
                    return new Result(stats, failed, true);
                } catch (ExecutionException ex) {
                    // One image failing (an Error such as OutOfMemoryError included)
                    // must not discard the results of the others.
                    logger.warn(
                            "[SP Classify] Pixel prescreen failed to read '{}': {}",
                            name,
                            String.valueOf(ex.getCause()));
                    failed.add(name);
                }
            }
            return new Result(stats, failed, wasCancelled);
        } finally {
            pool.shutdown();
        }
    }

    private static ImagePixelStats.ImageStats read(ProjectImageEntry<BufferedImage> entry) throws Exception {
        try (var server = entry.getServerBuilder().build()) {
            return ImagePixelStatsReader.read(entry.getImageName(), server)
                    .withIdentity(entry.getImageName(), entry.getID());
        }
    }
}
