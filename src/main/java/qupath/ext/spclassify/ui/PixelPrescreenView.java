package qupath.ext.spclassify.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.io.PixelStatsCsvExporter;
import qupath.ext.spclassify.io.ProjectStateManager;
import qupath.ext.spclassify.model.PixelCohortAnalyzer;
import qupath.ext.spclassify.model.PixelCohortReport;
import qupath.ext.spclassify.model.PixelPrescreenCache;
import qupath.ext.spclassify.model.PixelPrescreenScanner;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Dialog summarising whole-image pixel statistics across a project: a verdict,
 * an anomaly score, and the headline per-channel summary for each image, with a
 * plain-English review of how the selected image compares to the cohort.
 * <p>
 * This is the pixel-level twin of {@link ProjectPredictionSummaryView}: it works
 * straight off the low-resolution pyramid and needs no cells, so it can be run as
 * a prescreen at the very start of a project to spot background-heavy,
 * over-exposed, or weakly-stained images.
 * <p>
 * The raw per-image statistics are saved to {@code <project>/celltune/pixel-prescreen.json}
 * after every scan and loaded the next time the window is opened, including after a
 * QuPath restart. Images added to the project later can be scanned on their own
 * ("Scan new images"); the cohort report is recomputed from the saved statistics of
 * every image still in the project. One window is kept per project: reopening it
 * brings the existing window to the front.
 */
public class PixelPrescreenView {

    private static final Logger logger = LoggerFactory.getLogger(PixelPrescreenView.class);

    private static final String TITLE = "Image Pixel Prescreen";

    private static final DateTimeFormatter SCAN_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** The window for the current project, kept so reopening it does not rescan. */
    private static PixelPrescreenView current;

    private final Stage stage;
    private final QuPathGUI qupath;
    private final Project<BufferedImage> project;
    private PixelPrescreenCache cache;
    private PixelCohortReport report = new PixelCohortReport(List.of());
    private int missingCount;
    private boolean scanning;
    private final ObservableList<PixelCohortReport.ImageReport> backing = FXCollections.observableArrayList();
    private final TableView<PixelCohortReport.ImageReport> table;
    private final FilteredList<PixelCohortReport.ImageReport> filteredRows;
    private final Label summaryLabel;
    private final Label scanLabel;
    private final TextArea detailsArea;
    private final CheckBox flaggedOnlyBox;
    private final Button scanNewButton;
    private final Button rescanAllButton;

    /**
     * Open the prescreen for the current project. Shows the existing window if there is
     * one, otherwise the saved results, otherwise scans every image first.
     */
    public static void open(QuPathGUI qupath) {
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) {
            Dialogs.showErrorMessage(TITLE, "Open a QuPath project first.");
            return;
        }
        if (project.getImageList().isEmpty()) {
            Dialogs.showInfoNotification(TITLE, "No project images found.");
            return;
        }
        if (current != null) {
            if (current.project == project) {
                current.refresh();
                current.stage.show();
                current.stage.toFront();
                return;
            }
            current.stage.close();
            current = null;
        }

        PixelPrescreenCache saved = ProjectStateManager.loadPixelPrescreen(project);
        if (saved != null && !saved.isCurrent()) {
            Dialogs.showInfoNotification(
                    TITLE, "Saved prescreen results were made by an older version and will be recomputed.");
            saved = null;
        }
        var view = new PixelPrescreenView(qupath, project, saved == null ? PixelPrescreenCache.empty() : saved);
        current = view;
        if (view.report.images().isEmpty()) {
            view.scan(projectEntries(project), true);
        } else {
            view.stage.show();
        }
    }

    private PixelPrescreenView(QuPathGUI qupath, Project<BufferedImage> project, PixelPrescreenCache cache) {
        this.qupath = qupath;
        this.project = project;
        this.cache = cache;

        filteredRows = new FilteredList<>(backing, row -> true);
        table = new TableView<>(filteredRows);

        TableColumn<PixelCohortReport.ImageReport, String> imageCol = new TableColumn<>("Image");
        imageCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().imageName()));
        imageCol.setMinWidth(240);

        TableColumn<PixelCohortReport.ImageReport, String> verdictCol = new TableColumn<>("Verdict");
        verdictCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().verdict()));
        verdictCol.setMinWidth(120);

        TableColumn<PixelCohortReport.ImageReport, Number> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(
                cd -> new SimpleObjectProperty<>(cd.getValue().score()));
        scoreCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PixelCohortReport.ImageReport, String> fgCol = new TableColumn<>("Foreground %");
        fgCol.setCellValueFactory(
                cd -> new SimpleStringProperty(pct(cd.getValue().meanForegroundCoverage())));
        fgCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PixelCohortReport.ImageReport, String> emptyCol = new TableColumn<>("Empty %");
        emptyCol.setCellValueFactory(
                cd -> new SimpleStringProperty(pct(cd.getValue().emptyFraction())));
        emptyCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PixelCohortReport.ImageReport, String> satCol = new TableColumn<>("Max sat %");
        satCol.setCellValueFactory(
                cd -> new SimpleStringProperty(pct(cd.getValue().maxSaturationFraction())));
        satCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PixelCohortReport.ImageReport, String> dynCol = new TableColumn<>("Dyn. range");
        dynCol.setCellValueFactory(
                cd -> new SimpleStringProperty(num(cd.getValue().medianDynamicRange())));
        dynCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PixelCohortReport.ImageReport, String> focusCol = new TableColumn<>("Focus");
        focusCol.setCellValueFactory(
                cd -> new SimpleStringProperty(num(cd.getValue().maxFocus())));
        focusCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PixelCohortReport.ImageReport, String> intensityCol = new TableColumn<>("Intensity z");
        intensityCol.setCellValueFactory(
                cd -> new SimpleStringProperty(num(cd.getValue().maxIntensityZ())));
        intensityCol.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<PixelCohortReport.ImageReport, String> flaggedCol = new TableColumn<>("Flagged");
        flaggedCol.setCellValueFactory(
                cd -> new SimpleStringProperty(cd.getValue().flagged() ? "Yes" : "No"));
        flaggedCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().add(imageCol);
        table.getColumns().add(verdictCol);
        table.getColumns().add(scoreCol);
        table.getColumns().add(fgCol);
        table.getColumns().add(emptyCol);
        table.getColumns().add(satCol);
        table.getColumns().add(dynCol);
        table.getColumns().add(focusCol);
        table.getColumns().add(intensityCol);
        table.getColumns().add(flaggedCol);

        scoreCol.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(scoreCol);

        summaryLabel = new Label();
        summaryLabel.setStyle("-fx-font-weight: bold;");

        detailsArea = new TextArea();
        detailsArea.setEditable(false);
        detailsArea.setWrapText(true);
        detailsArea.setPrefRowCount(10);

        table.getSelectionModel().selectedItemProperty().addListener((obs, oldRow, newRow) -> showDetailsFor(newRow));

        flaggedOnlyBox = new CheckBox("Flagged only");
        flaggedOnlyBox.selectedProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        scanLabel = new Label();

        HBox filtersRow = new HBox(10, flaggedOnlyBox);
        filtersRow.setAlignment(Pos.CENTER_LEFT);

        scanNewButton = new Button("Scan New Images");
        scanNewButton.setOnAction(e -> scanMissing());
        rescanAllButton = new Button("Rescan All");
        rescanAllButton.setOnAction(e -> rescanAll());

        Button openImageButton = new Button("Open Selected Image");
        openImageButton.setOnAction(e -> openSelectedImage());

        Button exportCsvButton = new Button("Export CSV");
        exportCsvButton.setOnAction(e -> exportCsv());

        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> closeButton.getScene().getWindow().hide());

        HBox buttonRow = new HBox(8, openImageButton, exportCsvButton, scanNewButton, rescanAllButton, closeButton);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        Label detailsLabel = new Label("Selected image review:");

        VBox root = new VBox(8, summaryLabel, scanLabel, filtersRow, table, detailsLabel, detailsArea, buttonRow);
        root.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);

        stage = new Stage();
        stage.initOwner(qupath.getStage());
        stage.initModality(Modality.NONE);
        stage.setTitle(TITLE);
        stage.setScene(new Scene(root, 1040, 640));

        refresh();
    }

    @SuppressWarnings("unchecked")
    private static List<ProjectImageEntry<BufferedImage>> projectEntries(Project<BufferedImage> project) {
        return (List<ProjectImageEntry<BufferedImage>>) (List<?>) project.getImageList();
    }

    /** Entry ID → current image name, in project order. */
    private Map<String, String> projectImages() {
        var map = new LinkedHashMap<String, String>();
        for (var entry : projectEntries(project)) {
            if (entry != null) {
                map.put(entry.getID(), entry.getImageName());
            }
        }
        return map;
    }

    /** Recompute the report from the saved stats of the images currently in the project. */
    private void refresh() {
        var images = projectImages();
        report = PixelCohortAnalyzer.analyze(cache.forProject(images));
        missingCount = cache.missingIds(images.keySet()).size();
        var selected = table.getSelectionModel().getSelectedItem();
        backing.setAll(report.images());
        if (selected != null) {
            backing.stream()
                    .filter(r -> r.imageId() != null && r.imageId().equals(selected.imageId()))
                    .findFirst()
                    .ifPresent(r -> table.getSelectionModel().select(r));
        }
        String when = cache.scannedAt() == null ? "never" : formatScanTime(cache.scannedAt());
        scanLabel.setText(String.format(
                Locale.ROOT,
                "Last scan: %s | images not scanned: %d%s",
                when,
                missingCount,
                missingCount > 0 ? " (use Scan New Images)" : ""));
        updateButtons();
        applyFilters();
    }

    private static String formatScanTime(String iso) {
        try {
            return SCAN_TIME_FORMAT.format(Instant.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }

    private void updateButtons() {
        scanNewButton.setText(missingCount > 0 ? "Scan New Images (" + missingCount + ")" : "Scan New Images");
        scanNewButton.setDisable(scanning || missingCount == 0);
        rescanAllButton.setDisable(scanning);
    }

    private boolean projectStillOpen() {
        if (qupath.getProject() != project) {
            Dialogs.showErrorMessage(TITLE, "The project this prescreen belongs to is no longer open.");
            return false;
        }
        return true;
    }

    private void scanMissing() {
        if (!projectStillOpen()) {
            return;
        }
        var missing = new java.util.HashSet<>(cache.missingIds(projectImages().keySet()));
        var entries = projectEntries(project).stream()
                .filter(e -> e != null && missing.contains(e.getID()))
                .toList();
        scan(entries, false);
    }

    private void rescanAll() {
        if (!projectStillOpen()) {
            return;
        }
        cache = PixelPrescreenCache.empty();
        scan(projectEntries(project), false);
    }

    /**
     * Read {@code entries} off the FX thread behind a cancellable progress dialog, add
     * the results to the saved stats, save them, and refresh the table. Images read
     * before a cancel are kept and saved.
     *
     * @param showWhenDone show this window when the scan finishes (first scan)
     */
    private void scan(List<ProjectImageEntry<BufferedImage>> entries, boolean showWhenDone) {
        if (entries.isEmpty()) {
            return;
        }
        scanning = true;
        updateButtons();

        var cancelled = new AtomicBoolean(false);
        var progressStage = new Stage();
        progressStage.setTitle(TITLE + " — Reading images");
        progressStage.initOwner(qupath.getStage());
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setResizable(false);
        var bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        var status = new Label("Reading images: 0 / " + entries.size() + "…");
        status.setMaxWidth(360);
        status.setWrapText(true);
        var cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> {
            cancelled.set(true);
            cancelButton.setDisable(true);
            status.setText("Cancelling — finishing the images already being read…");
        });
        var box = new VBox(8, status, bar, cancelButton);
        box.setPadding(new Insets(15));
        progressStage.setScene(new Scene(box));
        progressStage.setOnCloseRequest(e -> {
            e.consume();
            cancelButton.fire();
        });
        progressStage.show();

        int total = entries.size();
        Thread worker = new Thread(
                () -> {
                    var result = PixelPrescreenScanner.scan(
                            entries,
                            done -> Platform.runLater(() -> {
                                bar.setProgress((double) done / total);
                                if (!cancelled.get()) {
                                    status.setText(String.format("Reading images: %d / %d…", done, total));
                                }
                            }),
                            cancelled::get);
                    Platform.runLater(() -> finishScan(result, progressStage, showWhenDone));
                },
                "sp-classify-pixel-prescreen");
        worker.setDaemon(true);
        worker.start();
    }

    private void finishScan(PixelPrescreenScanner.Result result, Stage progressStage, boolean showWhenDone) {
        progressStage.close();
        scanning = false;
        if (!result.stats().isEmpty()) {
            cache = cache.merge(result.stats(), Instant.now().toString());
            try {
                ProjectStateManager.savePixelPrescreen(project, cache);
            } catch (IOException ex) {
                logger.warn("[SP Classify] Could not save pixel prescreen results: {}", ex.getMessage());
                Dialogs.showWarningNotification(TITLE, "Results could not be saved: " + ex.getMessage());
            }
        }
        refresh();

        if (!result.failed().isEmpty()) {
            Dialogs.showWarningNotification(TITLE, result.failed().size() + " image(s) could not be read (see log).");
        }
        if (result.cancelled()) {
            Dialogs.showInfoNotification(
                    TITLE,
                    "Scan cancelled. " + result.stats().size()
                            + " image(s) read and saved; use Scan New Images to continue.");
        }
        if (report.images().isEmpty()) {
            if (!result.cancelled()) {
                Dialogs.showErrorMessage(TITLE, "Could not read pixels from any project image (see log).");
            }
            if (current == this && !stage.isShowing()) {
                current = null;
            }
            return;
        }
        if (showWhenDone || stage.isShowing()) {
            stage.show();
            stage.toFront();
        }
    }

    private void applyFilters() {
        boolean flaggedOnly = flaggedOnlyBox.isSelected();
        filteredRows.setPredicate(row -> row != null && (!flaggedOnly || row.flagged()));

        if (filteredRows.isEmpty()) {
            table.getSelectionModel().clearSelection();
            detailsArea.setText("No images match the current filter.");
        } else if (!filteredRows.contains(table.getSelectionModel().getSelectedItem())) {
            table.getSelectionModel().selectFirst();
        }
        updateSummaryLabel();
        table.sort();
    }

    private void updateSummaryLabel() {
        long flaggedCount = report.images().stream()
                .filter(PixelCohortReport.ImageReport::flagged)
                .count();
        summaryLabel.setText(String.format(
                Locale.ROOT,
                "Displayed: %d / %d images | flagged: %d",
                filteredRows.size(),
                report.images().size(),
                flaggedCount));
    }

    private void showDetailsFor(PixelCohortReport.ImageReport row) {
        if (row == null) {
            detailsArea.setText("Select an image to view its review.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(row.narrative()).append("\n\n");
        sb.append("Per-channel (median | p99 | foreground% | dyn.range | sat% | focus):\n");
        for (var cc : row.channels()) {
            var s = cc.stats();
            sb.append(String.format(
                    Locale.ROOT,
                    "  %-16s %8s | %8s | %7s | %8s | %6s | %8s%n",
                    truncate(cc.channel(), 16),
                    num(s.median()),
                    num(s.p99()),
                    pct(s.foregroundCoverage()),
                    num(s.dynamicRange()),
                    pct(s.saturationFraction()),
                    num(s.laplacianVariance())));
        }
        detailsArea.setText(sb.toString());
    }

    private void openSelectedImage() {
        PixelCohortReport.ImageReport selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            Dialogs.showWarningNotification(TITLE, "Select an image row first.");
            return;
        }
        if (!projectStillOpen()) {
            return;
        }

        var entryOpt = projectEntries(project).stream()
                .filter(e -> e != null
                        && (selected.imageId() != null
                                ? selected.imageId().equals(e.getID())
                                : selected.imageName().equals(e.getImageName())))
                .findFirst();
        if (entryOpt.isEmpty()) {
            Dialogs.showErrorMessage(TITLE, "Could not find image in project: " + selected.imageName());
            return;
        }

        // Suppress QuPath's "save changes?" prompt without writing — the user is
        // just navigating between images (mirrors ProjectPredictionSummaryView).
        var currentImageData = qupath.getImageData();
        if (currentImageData != null) {
            try {
                currentImageData.setChanged(false);
            } catch (Exception ex) {
                // Non-fatal.
            }
        }
        if (!qupath.openImageEntry(entryOpt.get())) {
            Dialogs.showErrorMessage(TITLE, "QuPath could not open image: " + selected.imageName());
        }
    }

    private void exportCsv() {
        if (report.images().isEmpty()) {
            Dialogs.showInfoNotification(TITLE, "No rows available to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Image Pixel Prescreen CSV");
        chooser.setInitialFileName("sp-classify_image_pixel_prescreen.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        File out = chooser.showSaveDialog(stage);
        if (out == null) {
            return;
        }
        try {
            PixelStatsCsvExporter.writeCsv(out.toPath(), report);
            Dialogs.showInfoNotification(TITLE, "Exported CSV: " + out.getAbsolutePath());
        } catch (IOException ex) {
            Dialogs.showErrorMessage(TITLE, "Failed to export CSV: " + ex.getMessage());
        }
    }

    private static String pct(double frac) {
        return Double.isNaN(frac) ? "-" : String.format(Locale.ROOT, "%.1f%%", frac * 100.0);
    }

    private static String num(double v) {
        if (Double.isNaN(v)) {
            return "-";
        }
        return Math.abs(v) >= 1000.0 ? String.format(Locale.ROOT, "%.0f", v) : String.format(Locale.ROOT, "%.2f", v);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
