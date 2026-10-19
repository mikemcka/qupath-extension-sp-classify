package qupath.ext.spclassify;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.io.PredictionCountsCache;
import qupath.ext.spclassify.model.CohortAnomalyAnalyzer;
import qupath.ext.spclassify.model.CohortAnomalyReport;
import qupath.ext.spclassify.model.PopulationSet;
import qupath.ext.spclassify.model.PredictionCounts;
import qupath.ext.spclassify.ui.ProjectPredictionSummaryView;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.ProjectImageEntry;

/**
 * The <b>Project Prediction Summary</b> dialog: scans every project image for saved
 * predictions, computes per-image agreement/disagreement counts and cohort-level anomaly
 * flags, and shows them in a {@link ProjectPredictionSummaryView}.
 * <p>
 * Loading runs off the FX thread behind a small progress dialog. Saved predictions are
 * counted by streaming each file (never loading every cell's probabilities), on
 * {@link #LOAD_THREADS} workers, and the counts are kept in {@link PredictionCountsCache}
 * so reopening the summary only re-reads files that changed. Lifted verbatim out of {@code SpClassifyExtension} (mirroring the
 * {@code UtilityScripts}/{@code AnnotationRegionExporter} moves); the menu handler delegates
 * here. The only state it needs is the current image's live in-memory predictions, passed in
 * as a parameter — the caller persists them first so they are reflected in the summary.
 */
final class ProjectPredictionSummary {

    private static final ResourceBundle resources = ResourceBundle.getBundle("qupath.ext.spclassify.ui.strings");
    private static final String EXTENSION_NAME = resources.getString("name");
    private static final Logger logger = LoggerFactory.getLogger(ProjectPredictionSummary.class);

    private ProjectPredictionSummary() {} // utility class

    /**
     * Build and show the project prediction summary.
     *
     * @param qupath                 the QuPath GUI
     * @param liveCurrentPredictions the open image's in-memory predictions (may be null); used
     *                               in place of the saved file for the current image
     * @param scope                  the active binary marker, or null for multi-class
     * @param classNames             the loaded classifier's classes (may be null); saved
     *                               predictions made with a different class list are not counted
     */
    static void show(QuPathGUI qupath, PopulationSet liveCurrentPredictions, String scope, List<String> classNames) {
        var project = qupath.getProject();
        if (project == null) {
            Dialogs.showErrorMessage(EXTENSION_NAME, resources.getString("classify.no_project"));
            return;
        }

        String currentImageName = null;
        var currentImageData = qupath.getImageData();
        if (currentImageData != null) {
            var currentEntry = project.getEntry(currentImageData);
            if (currentEntry != null) {
                currentImageName = currentEntry.getImageName();
            }
        }
        final String currentImageNameFinal = currentImageName;
        final PopulationSet liveCurrent = liveCurrentPredictions;

        @SuppressWarnings("unchecked")
        var entries = (List<ProjectImageEntry<BufferedImage>>) (List<?>) project.getImageList();
        var imageNames = new ArrayList<String>(entries.size());
        for (var entry : entries) {
            if (entry == null || entry.getImageName() == null) continue;
            imageNames.add(entry.getImageName());
        }

        if (imageNames.isEmpty()) {
            Dialogs.showInfoNotification(EXTENSION_NAME, "No project images found.");
            return;
        }

        // ── Show a small progress dialog and count all images off the FX thread
        // so the UI stays responsive on large projects.
        var stage = new javafx.stage.Stage();
        stage.setTitle(EXTENSION_NAME + " — Loading prediction summary");
        stage.initOwner(qupath.getStage());
        stage.initModality(javafx.stage.Modality.WINDOW_MODAL);
        stage.setResizable(false);
        var bar = new javafx.scene.control.ProgressBar(0);
        bar.setPrefWidth(360);
        var status = new javafx.scene.control.Label("Scanning project images…");
        status.setMaxWidth(360);
        status.setWrapText(true);
        var box = new javafx.scene.layout.VBox(8, status, bar);
        box.setPadding(new javafx.geometry.Insets(15));
        stage.setScene(new javafx.scene.Scene(box));
        stage.setOnCloseRequest(e -> e.consume());
        stage.show();

        Thread t = new Thread(
                () -> {
                    List<ProjectPredictionSummaryView.Row> rows = null;
                    try {
                        int total = imageNames.size();
                        var done = new java.util.concurrent.atomic.AtomicInteger();
                        var cache = PredictionCountsCache.open(project);
                        var sourceRows = loadInputRows(
                                imageNames,
                                name -> {
                                    // The open image's in-memory predictions come from the loaded model.
                                    if (name.equals(currentImageNameFinal)
                                            && liveCurrent != null
                                            && liveCurrent.size() > 0) {
                                        return inputRow(name, PredictionCounts.of(liveCurrent), null);
                                    }
                                    return inputRow(name, cache.get(scope, name), classNames);
                                },
                                () -> {
                                    int c = done.incrementAndGet();
                                    javafx.application.Platform.runLater(() -> {
                                        bar.setProgress((double) c / total);
                                        status.setText(String.format("Loading predictions: %d / %d images…", c, total));
                                    });
                                });
                        cache.save();
                        rows = buildRows(sourceRows);
                    } catch (Throwable ex) {
                        logger.error("Project prediction summary failed", ex);
                    }
                    final var rowsF = rows;
                    // Always close the progress window: it cannot be closed by hand.
                    javafx.application.Platform.runLater(() -> {
                        stage.close();
                        if (rowsF == null) {
                            Dialogs.showErrorMessage(EXTENSION_NAME, "Could not build the project summary (see log).");
                            return;
                        }
                        ProjectPredictionSummaryView.showOrUpdate(qupath, project, rowsF);
                    });
                },
                "sp-classify-summary-load");
        t.setDaemon(true);
        t.start();
    }

    /** Images whose predictions are counted at the same time. */
    static final int LOAD_THREADS = 4;

    /** Reads one image's input row; may throw. */
    interface RowLoader {
        SummaryInputRow load(String imageName) throws Exception;
    }

    /**
     * Build one input row per image on {@link #LOAD_THREADS} workers, in image order.
     * An image whose predictions cannot be read gets a {@link #READ_ERROR} row instead
     * of stopping the others.
     */
    static List<SummaryInputRow> loadInputRows(List<String> imageNames, RowLoader loader, Runnable onImageDone)
            throws InterruptedException {
        var pool = qupath.ext.spclassify.util.BackgroundExecutors.newFixedPool(
                Math.max(1, Math.min(LOAD_THREADS, imageNames.size())), "sp-classify-summary-count");
        try {
            var futures = new ArrayList<java.util.concurrent.Future<SummaryInputRow>>(imageNames.size());
            for (String name : imageNames) {
                futures.add(pool.submit(() -> {
                    try {
                        return loader.load(name);
                    } finally {
                        onImageDone.run();
                    }
                }));
            }
            var out = new ArrayList<SummaryInputRow>(imageNames.size());
            for (int i = 0; i < imageNames.size(); i++) {
                try {
                    out.add(futures.get(i).get());
                } catch (java.util.concurrent.ExecutionException ex) {
                    logger.warn(
                            "Failed to read predictions for {} in project summary: {}",
                            imageNames.get(i),
                            String.valueOf(ex.getCause()));
                    out.add(new SummaryInputRow(imageNames.get(i), 0L, 0L, READ_ERROR, null));
                }
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Score the images that have counts against each other and build the table rows. */
    static List<ProjectPredictionSummaryView.Row> buildRows(List<SummaryInputRow> sourceRows) {
        var analyzerInputs = new ArrayList<CohortAnomalyReport.ImageInput>(sourceRows.size());
        for (var src : sourceRows) {
            if (src.predictedCells() > 0 && src.avgCounts() != null) {
                analyzerInputs.add(new CohortAnomalyReport.ImageInput(
                        src.imageName(), src.predictedCells(), src.disagreements(), src.avgCounts()));
            }
        }
        var anomalyByImage = CohortAnomalyAnalyzer.analyze(analyzerInputs).byImageName();
        var rows = new ArrayList<ProjectPredictionSummaryView.Row>(sourceRows.size());
        for (var source : sourceRows) {
            rows.add(buildPredictionSummaryRow(source, anomalyByImage.get(source.imageName())));
        }
        return rows;
    }

    static final String NO_PREDICTIONS = "No saved predictions for this image.";
    static final String READ_ERROR = "Saved predictions could not be read (see log).";
    static final String DIFFERENT_CLASSES =
            "Predictions made with a different class list. Apply the classifier to this image again.";

    /**
     * One image's summary input. Predictions made with a class list other than
     * {@code classNames} get no counts, so they are left out of the cohort anomaly scoring.
     *
     * @param classNames the loaded classifier's classes, or null to skip the check
     */
    static SummaryInputRow inputRow(String imageName, PopulationSet predictions, List<String> classNames) {
        return inputRow(imageName, PredictionCounts.of(predictions), classNames);
    }

    /** As above, from counts (see {@link PredictionCounts#of}). */
    static SummaryInputRow inputRow(String imageName, PredictionCounts counts, List<String> classNames) {
        if (counts == null || counts.cells() == 0) {
            return new SummaryInputRow(imageName, 0L, 0L, NO_PREDICTIONS, null);
        }
        if (classNames != null && !classNames.equals(counts.classNames())) {
            return new SummaryInputRow(imageName, 0L, 0L, DIFFERENT_CLASSES, null);
        }
        Map<String, Long> avgCounts = counts.avgCounts();
        return new SummaryInputRow(
                imageName, counts.cells(), counts.disagreements(), formatClassCounts(avgCounts), avgCounts);
    }

    private static ProjectPredictionSummaryView.Row buildPredictionSummaryRow(
            SummaryInputRow source, CohortAnomalyReport.ImageAnomaly anomaly) {
        if (source.predictedCells() == 0L) {
            return new ProjectPredictionSummaryView.Row(
                    source.imageName(),
                    0L,
                    0L,
                    0L,
                    Double.NaN,
                    0.0,
                    false,
                    "-",
                    "No highlighted rare classes.",
                    List.of(),
                    source.classCountsText());
        }

        long agreements = Math.max(0L, source.predictedCells() - source.disagreements());
        double agreementPct = source.predictedCells() > 0 ? (100.0 * agreements) / source.predictedCells() : 0.0;

        if (anomaly == null) {
            return new ProjectPredictionSummaryView.Row(
                    source.imageName(),
                    source.predictedCells(),
                    agreements,
                    source.disagreements(),
                    agreementPct,
                    0.0,
                    false,
                    "-",
                    "No highlighted rare classes.",
                    List.of(),
                    source.classCountsText());
        }

        return new ProjectPredictionSummaryView.Row(
                source.imageName(),
                source.predictedCells(),
                agreements,
                source.disagreements(),
                agreementPct,
                anomaly.anomalyScore(),
                anomaly.flagged(),
                formatFlagReasons(anomaly.flagReasons()),
                formatHighlightedRareClasses(anomaly.highlightedClasses(), anomaly.enrichmentByClass()),
                anomaly.highlightedClasses(),
                source.classCountsText());
    }

    private static String formatClassCounts(Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return "No predicted classes.";
        }

        var parts = new ArrayList<String>(counts.size());
        for (var entry : counts.entrySet()) {
            String className = entry.getKey() == null ? "(unknown)" : entry.getKey();
            long count = entry.getValue() == null ? 0L : entry.getValue();
            parts.add(className + ": " + count);
        }
        return String.join(", ", parts);
    }

    private static String formatFlagReasons(List<String> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            return "-";
        }
        return String.join(", ", reasons);
    }

    private static String formatHighlightedRareClasses(
            List<String> highlightedClasses, Map<String, CohortAnomalyReport.ClassEnrichment> enrichmentByClass) {
        if (highlightedClasses == null || highlightedClasses.isEmpty()) {
            return "No highlighted rare classes.";
        }

        var parts = new ArrayList<String>(highlightedClasses.size());
        for (String className : highlightedClasses) {
            var enrichment = enrichmentByClass == null ? null : enrichmentByClass.get(className);
            if (enrichment == null) {
                parts.add(className);
                continue;
            }
            parts.add(String.format(
                    "%s (count=%d, fold=%.2fx)", className, enrichment.count(), enrichment.enrichmentFold()));
        }
        return String.join("; ", parts);
    }

    record SummaryInputRow(
            String imageName,
            long predictedCells,
            long disagreements,
            String classCountsText,
            Map<String, Long> avgCounts) {}
}
