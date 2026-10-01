package qupath.ext.spclassify.ui;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.io.MarkerTableImporter;
import qupath.ext.spclassify.io.ProjectStateManager;
import qupath.ext.spclassify.model.CellTypeTable;
import qupath.ext.spclassify.model.ChannelMappingModel;
import qupath.ext.spclassify.model.ChannelMappingModel.Row;
import qupath.ext.spclassify.model.ChannelMappingModel.Status;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Editor for the class → display-channel mapping used by review-mode auto channel switching.
 *
 * <p>Classes come from the project (plus the trained classifier and any existing table entry) and
 * channels from the open image, so the user picks rather than types and neither side can be
 * misspelled. Entries imported from a marker-table CSV are shown with how their names resolve on
 * this image (exact / normalized / substring / unmatched) and can be pinned to exact channel
 * names. CSV import/export is available inside the dialog and the Import ▸ Marker Table menu is
 * unchanged. All editing logic lives in the pure {@link ChannelMappingModel}; this class is view
 * and wiring only.
 *
 * <p>Non-modal, so the viewer stays usable for Preview; at most one instance is open at a time.
 */
public final class ChannelMappingDialog {

    private static final Logger logger = LoggerFactory.getLogger(ChannelMappingDialog.class);
    private static final ResourceBundle STRINGS = ResourceBundle.getBundle("qupath.ext.spclassify.ui.strings");
    private static final String TITLE = STRINGS.getString("channelmap.title");
    /** Above this many channels the composite gets hard to read; warn, never block. */
    static final int SOFT_CHANNEL_WARNING = 8;

    private static WeakReference<Stage> openStage = new WeakReference<>(null);

    private final QuPathGUI qupath;
    private final List<String> classNames;
    private final Consumer<CellTypeTable> onSaved;
    /** The project the mapping was opened for; Save refuses to write it into a different one. */
    private final Project<BufferedImage> project;

    private final Stage stage = new Stage();

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ListView<Row> rowList = new ListView<>(rows);
    private final ObservableList<ChannelItem> channelItems = FXCollections.observableArrayList();
    private final FilteredList<ChannelItem> filteredChannels = new FilteredList<>(channelItems, c -> true);
    private final ListView<ChannelItem> channelList = new ListView<>(filteredChannels);

    private final Label imageLabel = new Label();
    private final Label scanLabel = new Label();
    private final Label rowTitle = new Label();
    private final Label rowNote = new Label();
    private final Label countWarning = new Label();
    private final Label footerStatus = new Label();
    private final Button scanButton = new Button(STRINGS.getString("channelmap.scan"));

    private List<String> imageChannels = List.of();
    private boolean ruleFormat;
    private boolean dirty;
    /** Suppresses the checkbox listeners while the right-hand list is being re-synced to a row. */
    private boolean syncing;

    private Map<String, Integer> scanCounts = Map.of();
    private int scanImages;
    private AtomicBoolean scanCancel;
    private final ChangeListener<ImageData<BufferedImage>> imageListener = (obs, was, now) -> onImageChanged(now);

    /** One entry in the channel checklist. */
    static final class ChannelItem {
        final String name;
        final BooleanProperty selected = new SimpleBooleanProperty();
        int imageIndex = -1; // index in the open image, or -1 if absent

        ChannelItem(String name) {
            this.name = name;
        }
    }

    /**
     * Open the editor (or bring the open one to the front).
     *
     * @param qupath     QuPath instance; an image must be open (its channels are the choices)
     * @param owner      window to own the dialog (the review window when launched from there), or null
     * @param current    the current mapping, or null
     * @param classNames class names to offer, in display order
     * @param onSaved    receives the saved table (null when the mapping was cleared) after it is
     *                   persisted to the project
     */
    public static void show(
            QuPathGUI qupath,
            Window owner,
            CellTypeTable current,
            Collection<String> classNames,
            Consumer<CellTypeTable> onSaved) {
        Stage existing = openStage.get();
        if (existing != null && existing.isShowing()) {
            existing.toFront();
            existing.requestFocus();
            return;
        }
        ImageData<BufferedImage> imageData = qupath.getImageData();
        if (imageData == null) {
            Dialogs.showErrorMessage(TITLE, STRINGS.getString("channelmap.noimage"));
            return;
        }
        var dlg = new ChannelMappingDialog(qupath, owner, current, classNames, onSaved);
        openStage = new WeakReference<>(dlg.stage);
        dlg.stage.show();
    }

    private ChannelMappingDialog(
            QuPathGUI qupath,
            Window owner,
            CellTypeTable current,
            Collection<String> classNames,
            Consumer<CellTypeTable> onSaved) {
        this.qupath = qupath;
        this.classNames = classNames == null ? List.of() : List.copyOf(new LinkedHashSet<>(classNames));
        this.onSaved = onSaved;
        this.project = qupath.getProject();
        this.ruleFormat = current != null && current.hasGatingRules();
        rows.setAll(ChannelMappingModel.buildRows(current, this.classNames));

        buildUi(owner);
        onImageChanged(qupath.getImageData());
        qupath.imageDataProperty().addListener(imageListener);
        if (!rows.isEmpty()) rowList.getSelectionModel().select(0);
    }

    // ── Layout ──────────────────────────────────────────────────────────────

    private void buildUi(Window owner) {
        stage.setTitle(TITLE);
        Window effectiveOwner = owner != null ? owner : qupath.getStage();
        stage.initOwner(effectiveOwner);
        stage.initModality(Modality.NONE);
        // The review window is always-on-top; without this the editor opens behind it.
        if (effectiveOwner instanceof Stage s && s.isAlwaysOnTop()) stage.setAlwaysOnTop(true);

        Label intro = new Label(STRINGS.getString("channelmap.intro"));
        intro.setWrapText(true);

        scanButton.setTooltip(new Tooltip(STRINGS.getString("channelmap.scan.tooltip")));
        scanButton.setOnAction(e -> toggleScan());
        scanButton.setDisable(qupath.getProject() == null);
        scanLabel.setStyle("-fx-text-fill: #555;");
        HBox imageRow = new HBox(10, imageLabel, spacer(), scanLabel, scanButton);
        imageRow.setAlignment(Pos.CENTER_LEFT);

        // Left: classes
        rowList.setCellFactory(lv -> new RowCell());
        rowList.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> syncChannelChecks());
        Label classesLabel = new Label(STRINGS.getString("channelmap.classes"));
        classesLabel.setStyle("-fx-font-weight: bold;");
        VBox left = new VBox(4, classesLabel, rowList);
        VBox.setVgrow(rowList, Priority.ALWAYS);
        left.setMinWidth(220);

        // Right: channels for the selected class
        rowTitle.setStyle("-fx-font-weight: bold;");
        rowNote.setWrapText(true);
        rowNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #444;");
        rowNote.setMinHeight(Region.USE_PREF_SIZE);
        TextField filter = new TextField();
        filter.setPromptText(STRINGS.getString("channelmap.filter"));
        filter.textProperty().addListener((obs, was, now) -> {
            String f = now == null ? "" : now.strip().toLowerCase();
            filteredChannels.setPredicate(
                    c -> f.isEmpty() || c.name.toLowerCase().contains(f));
        });
        channelList.setCellFactory(lv -> {
            var cell = new CheckBoxListCell<ChannelItem>(item -> item.selected);
            cell.setConverter(new StringConverter<>() {
                @Override
                public String toString(ChannelItem item) {
                    return item == null ? "" : channelLabel(item);
                }

                @Override
                public ChannelItem fromString(String s) {
                    return null;
                }
            });
            return cell;
        });
        Button clearBtn = new Button(STRINGS.getString("channelmap.clear"));
        clearBtn.setTooltip(new Tooltip(STRINGS.getString("channelmap.clear.tooltip")));
        clearBtn.setOnAction(e -> clearSelectedRow());
        Button pinBtn = new Button(STRINGS.getString("channelmap.pin"));
        pinBtn.setTooltip(new Tooltip(STRINGS.getString("channelmap.pin.tooltip")));
        pinBtn.setOnAction(e -> pinRows(selectedRowAsList()));
        Button previewBtn = new Button(STRINGS.getString("channelmap.preview"));
        previewBtn.setTooltip(new Tooltip(STRINGS.getString("channelmap.preview.tooltip")));
        previewBtn.setOnAction(e -> previewSelectedRow());
        countWarning.setStyle("-fx-text-fill: #e65100; -fx-font-size: 11px;");
        HBox rowActions = new HBox(6, clearBtn, pinBtn, previewBtn, countWarning);
        rowActions.setAlignment(Pos.CENTER_LEFT);
        VBox right = new VBox(6, rowTitle, rowNote, filter, channelList, rowActions);
        VBox.setVgrow(channelList, Priority.ALWAYS);

        SplitPane split = new SplitPane(left, right);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.36);

        // Bottom: table-level actions
        Button importBtn = new Button(STRINGS.getString("channelmap.import"));
        importBtn.setTooltip(new Tooltip(STRINGS.getString("channelmap.import.tooltip")));
        importBtn.setOnAction(e -> importCsv());
        Button exportBtn = new Button(STRINGS.getString("channelmap.export"));
        exportBtn.setOnAction(e -> exportCsv());
        Button pinAllBtn = new Button(STRINGS.getString("channelmap.pinall"));
        pinAllBtn.setTooltip(new Tooltip(STRINGS.getString("channelmap.pinall.tooltip")));
        pinAllBtn.setOnAction(e -> pinRows(new ArrayList<>(rows)));
        Button saveBtn = new Button(STRINGS.getString("channelmap.save"));
        saveBtn.setDefaultButton(true);
        saveBtn.setOnAction(e -> save());
        Button cancelBtn = new Button(STRINGS.getString("channelmap.cancel"));
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> {
            if (confirmDiscard()) close();
        });
        footerStatus.setStyle("-fx-text-fill: #555; -fx-font-size: 11px;");
        footerStatus.setMinWidth(0);
        HBox bottom = new HBox(6, importBtn, exportBtn, pinAllBtn, footerStatus, spacer(), saveBtn, cancelBtn);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(6, intro, imageRow);
        BorderPane root = new BorderPane(split, top, null, bottom, null);
        BorderPane.setMargin(split, new Insets(8, 0, 8, 0));
        root.setPadding(new Insets(10));

        stage.setScene(new Scene(root, 860, 600));
        stage.setMinWidth(640);
        stage.setMinHeight(420);
        stage.setOnCloseRequest(e -> {
            if (!confirmDiscard()) e.consume();
        });
        stage.setOnHidden(e -> {
            qupath.imageDataProperty().removeListener(imageListener);
            if (scanCancel != null) scanCancel.set(true);
        });
    }

    private static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ── Channels ────────────────────────────────────────────────────────────

    private void onImageChanged(ImageData<BufferedImage> imageData) {
        if (imageData == null) {
            imageChannels = List.of();
            imageLabel.setText(STRINGS.getString("channelmap.noimage.short"));
        } else {
            imageChannels = channelNames(imageData.getServer());
            String name = imageName(imageData);
            imageLabel.setText(String.format(STRINGS.getString("channelmap.image"), name, imageChannels.size()));
        }
        rebuildChannelItems();
        rowList.refresh();
    }

    private String imageName(ImageData<BufferedImage> imageData) {
        Project<BufferedImage> project = qupath.getProject();
        if (project != null) {
            ProjectImageEntry<BufferedImage> entry = project.getEntry(imageData);
            if (entry != null) return entry.getImageName();
        }
        return imageData.getServer().getMetadata().getName();
    }

    static List<String> channelNames(ImageServer<?> server) {
        List<String> names = new ArrayList<>();
        int i = 1;
        for (ImageChannel ch : server.getMetadata().getChannels()) {
            String n = ch.getName();
            names.add(n == null || n.isBlank() ? "Channel " + i : n);
            i++;
        }
        return names;
    }

    /**
     * The checklist: the open image's channels in order, then channels seen only in other images
     * (after a project scan), then exact names stored in the mapping that neither has.
     */
    private void rebuildChannelItems() {
        Set<String> names = new LinkedHashSet<>(imageChannels);
        scanCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .forEach(e -> names.add(e.getKey()));
        for (Row r : rows) names.addAll(r.explicitChannels());

        List<ChannelItem> items = new ArrayList<>();
        for (String n : names) {
            ChannelItem item = new ChannelItem(n);
            item.imageIndex = imageChannels.indexOf(n);
            item.selected.addListener((obs, was, now) -> onChannelToggled(item, now));
            items.add(item);
        }
        syncing = true;
        try {
            channelItems.setAll(items);
        } finally {
            syncing = false;
        }
        syncChannelChecks();
    }

    private String channelLabel(ChannelItem item) {
        StringBuilder sb = new StringBuilder(item.name);
        if (item.imageIndex >= 0) {
            sb.append("   (C").append(item.imageIndex + 1).append(')');
        } else {
            sb.append("   — ").append(STRINGS.getString("channelmap.notinimage"));
        }
        if (scanImages > 0) {
            int n = scanCounts.getOrDefault(item.name, 0);
            if (n < scanImages) sb.append(String.format("   [%d/%d images]", n, scanImages));
        }
        return sb.toString();
    }

    // ── Row ⇄ checklist sync ────────────────────────────────────────────────

    private Row selectedRow() {
        return rowList.getSelectionModel().getSelectedItem();
    }

    private List<Row> selectedRowAsList() {
        Row r = selectedRow();
        return r == null ? List.of() : List.of(r);
    }

    /** Re-tick the checklist to show the selected row: its explicit channels, or what it resolves to. */
    private void syncChannelChecks() {
        Row row = selectedRow();
        Set<String> ticked = new LinkedHashSet<>();
        if (row != null) {
            ticked.addAll(
                    row.hasExplicitChannels()
                            ? row.explicitChannels()
                            : ChannelMappingModel.resolvedChannels(row, imageChannels));
        }
        syncing = true;
        try {
            for (ChannelItem item : channelItems) item.selected.set(ticked.contains(item.name));
        } finally {
            syncing = false;
        }
        updateRowDetails(row);
    }

    private void onChannelToggled(ChannelItem item, boolean selected) {
        if (syncing) return;
        Row row = selectedRow();
        if (row == null) {
            syncing = true;
            item.selected.set(false);
            syncing = false;
            return;
        }
        boolean wasLegacy = !row.hasExplicitChannels();
        row.setChannelSelected(item.name, selected, imageChannels);
        markDirty();
        // Seeding a legacy row may tick channels other than the one clicked; re-sync once.
        if (wasLegacy) syncChannelChecks();
        else updateRowDetails(row);
        rowList.refresh();
    }

    private void updateRowDetails(Row row) {
        if (row == null) {
            rowTitle.setText(STRINGS.getString("channelmap.selectclass"));
            rowNote.setText("");
            countWarning.setText("");
            return;
        }
        rowTitle.setText(String.format(STRINGS.getString("channelmap.channelsfor"), row.className()));
        rowNote.setText(describe(row));
        int n = row.effectiveNames().size();
        countWarning.setText(
                n > SOFT_CHANNEL_WARNING ? String.format(STRINGS.getString("channelmap.manychannels"), n) : "");
    }

    /** Plain-language explanation of a row's state, shown above the checklist. */
    private String describe(Row row) {
        List<String> lines = new ArrayList<>();
        if (!row.isKnownClass()) lines.add(String.format(STRINGS.getString("channelmap.note.orphan"), row.className()));
        if (row.isRenamed()) {
            lines.add(String.format(STRINGS.getString("channelmap.note.renamed"), row.sourceKey(), row.className()));
        }
        if (row.hasRule()) {
            lines.add(String.format(
                    STRINGS.getString("channelmap.note.rule"),
                    row.primaryExpression() == null ? "" : row.primaryExpression()));
        }
        if (row.hasExplicitChannels()) {
            if (row.explicitChannels().isEmpty()) {
                lines.add(
                        STRINGS.getString(row.hasRule() ? "channelmap.note.cleared.rule" : "channelmap.note.cleared"));
            } else {
                long missing = row.explicitChannels().stream()
                        .filter(c -> !imageChannels.contains(c))
                        .count();
                lines.add(STRINGS.getString("channelmap.note.explicit"));
                if (missing > 0) lines.add(String.format(STRINGS.getString("channelmap.note.missing"), missing));
            }
        } else if (!row.legacyMarkers().isEmpty()) {
            lines.add(
                    String.format(STRINGS.getString("channelmap.note.legacy"), String.join(", ", row.legacyMarkers())));
            for (var m : ChannelMappingModel.matches(row, imageChannels)) {
                switch (m.kind()) {
                    case NONE -> lines.add(String.format(STRINGS.getString("channelmap.note.nomatch"), m.query()));
                    case FUZZY ->
                        lines.add(String.format(
                                STRINGS.getString("channelmap.note.fuzzy"),
                                m.query(),
                                String.join(
                                        ", ",
                                        m.indices().stream()
                                                .map(imageChannels::get)
                                                .toList())));
                    default -> {}
                }
            }
        } else {
            lines.add(STRINGS.getString("channelmap.note.unmapped"));
        }
        return String.join("\n", lines);
    }

    // ── Actions ─────────────────────────────────────────────────────────────

    private void clearSelectedRow() {
        Row row = selectedRow();
        if (row == null) return;
        if (!row.isKnownClass()) {
            // An orphan has no class to keep an empty mapping for — remove it outright.
            rows.remove(row);
        } else {
            row.setExplicitChannels(List.of());
            syncChannelChecks();
        }
        markDirty();
        rowList.refresh();
    }

    private void pinRows(List<Row> targets) {
        List<String> problems = new ArrayList<>();
        int pinned = 0;
        for (Row r : targets) {
            if (!r.hasMapping()) continue;
            List<String> unresolved = ChannelMappingModel.pin(r, imageChannels);
            pinned++;
            if (!unresolved.isEmpty()) problems.add(r.className() + ": " + String.join(", ", unresolved));
        }
        if (pinned == 0) return;
        markDirty();
        rebuildChannelItems();
        rowList.refresh();
        if (!problems.isEmpty()) {
            Dialogs.builder()
                    .owner(stage)
                    .title(TITLE)
                    .contentText(STRINGS.getString("channelmap.pin.unresolved") + "\n\n" + String.join("\n", problems))
                    .buttons(javafx.scene.control.ButtonType.OK)
                    .showAndWait();
        }
    }

    private void previewSelectedRow() {
        Row row = selectedRow();
        if (row == null || !row.hasMapping()) {
            footerStatus.setText(STRINGS.getString("channelmap.preview.nothing"));
            return;
        }
        List<String> shown =
                ChannelSelector.applyToViewer(qupath, row.effectiveNames(), row.matchesExactFirst(), false);
        if (shown == null) footerStatus.setText(STRINGS.getString("channelmap.noimage.short"));
        else if (shown.isEmpty()) footerStatus.setText(STRINGS.getString("channelmap.preview.nomatch"));
        else
            footerStatus.setText(
                    String.format(STRINGS.getString("channelmap.preview.shown"), String.join(", ", shown)));
    }

    private void importCsv() {
        if (dirty || rows.stream().anyMatch(Row::hasMapping)) {
            boolean ok = Dialogs.builder()
                            .owner(stage)
                            .title(TITLE)
                            .contentText(STRINGS.getString("channelmap.import.confirm"))
                            .buttons(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL)
                            .showAndWait()
                            .orElse(javafx.scene.control.ButtonType.CANCEL)
                    == javafx.scene.control.ButtonType.OK;
            if (!ok) return;
        }
        FileChooser fc = new FileChooser();
        fc.setTitle(STRINGS.getString("channelmap.import"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        setInitialDir(fc);
        File chosen = fc.showOpenDialog(stage);
        if (chosen == null) return;
        try {
            CellTypeTable imported = MarkerTableImporter.importFromCSV(chosen.toPath());
            ruleFormat = imported.hasGatingRules();
            rows.setAll(ChannelMappingModel.buildRows(imported, classNames));
            markDirty();
            rebuildChannelItems();
            if (!rows.isEmpty()) rowList.getSelectionModel().select(0);
            footerStatus.setText(summarise(chosen.getName()));
        } catch (IOException ex) {
            logger.error("Failed to import marker table", ex);
            Dialogs.showErrorMessage(TITLE, "Import failed: " + ex.getMessage());
        }
    }

    /** "Loaded x.csv: 8 exact, 2 approximate, 1 unmatched, 1 orphan" for the current image. */
    private String summarise(String fileName) {
        int exact = 0, approx = 0, unmatched = 0, orphan = 0;
        for (Row r : rows) {
            if (!r.isKnownClass()) orphan++;
            if (!r.hasMapping()) continue;
            switch (ChannelMappingModel.status(r, imageChannels)) {
                case EXACT -> exact++;
                case NORMALIZED, FUZZY, PARTIAL -> approx++;
                case UNMATCHED -> unmatched++;
                default -> {}
            }
        }
        return String.format(
                STRINGS.getString("channelmap.import.summary"), fileName, exact, approx, unmatched, orphan);
    }

    private void exportCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle(STRINGS.getString("channelmap.export"));
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        fc.setInitialFileName("marker-table.csv");
        setInitialDir(fc);
        File chosen = fc.showSaveDialog(stage);
        if (chosen == null) return;
        try {
            ChannelMappingModel.toTable(rows, ruleFormat).saveToCSV(chosen.toPath());
            footerStatus.setText(String.format(STRINGS.getString("channelmap.export.done"), chosen.getName()));
        } catch (IOException ex) {
            logger.error("Failed to export marker table", ex);
            Dialogs.showErrorMessage(TITLE, "Export failed: " + ex.getMessage());
        }
    }

    private void setInitialDir(FileChooser fc) {
        var project = qupath.getProject();
        if (project != null && project.getPath() != null) {
            File dir = project.getPath().getParent().toFile();
            if (dir.isDirectory()) fc.setInitialDirectory(dir);
        }
    }

    private void save() {
        CellTypeTable table = ChannelMappingModel.toTable(rows, ruleFormat);
        if (qupath.getProject() != project) {
            Dialogs.showErrorMessage(TITLE, STRINGS.getString("channelmap.projectchanged"));
            return;
        }
        if (project != null) {
            try {
                ProjectStateManager.saveMarkerTable(project, table);
            } catch (IOException ex) {
                logger.error("Failed to save channel mapping", ex);
                Dialogs.showErrorMessage(TITLE, "Could not save the channel mapping: " + ex.getMessage());
                return;
            }
        }
        dirty = false;
        if (onSaved != null) onSaved.accept(table.isEmpty() ? null : table);
        Dialogs.showInfoNotification(TITLE, String.format(STRINGS.getString("channelmap.saved"), table.size()));
        close();
    }

    private void markDirty() {
        dirty = true;
    }

    private boolean confirmDiscard() {
        if (!dirty) return true;
        return Dialogs.builder()
                        .owner(stage)
                        .title(TITLE)
                        .contentText(STRINGS.getString("channelmap.discard"))
                        .buttons(javafx.scene.control.ButtonType.YES, javafx.scene.control.ButtonType.NO)
                        .showAndWait()
                        .orElse(javafx.scene.control.ButtonType.NO)
                == javafx.scene.control.ButtonType.YES;
    }

    private void close() {
        dirty = false;
        stage.hide();
    }

    // ── Project scan ────────────────────────────────────────────────────────

    private void toggleScan() {
        if (scanCancel != null) {
            scanCancel.set(true);
            return;
        }
        Project<BufferedImage> project = qupath.getProject();
        if (project == null) return;
        List<ProjectImageEntry<BufferedImage>> entries = new ArrayList<>(project.getImageList());
        AtomicBoolean cancel = new AtomicBoolean(false);
        scanCancel = cancel;
        scanButton.setText(STRINGS.getString("channelmap.scan.cancel"));

        Thread t = new Thread(
                () -> {
                    Map<String, Integer> counts = new LinkedHashMap<>();
                    int ok = 0, failed = 0;
                    for (int i = 0; i < entries.size() && !cancel.get(); i++) {
                        final int n = i + 1;
                        Platform.runLater(() -> scanLabel.setText(
                                String.format(STRINGS.getString("channelmap.scan.progress"), n, entries.size())));
                        var entry = entries.get(i);
                        try (ImageServer<BufferedImage> server =
                                entry.getServerBuilder().build()) {
                            for (String name : new LinkedHashSet<>(channelNames(server)))
                                counts.merge(name, 1, Integer::sum);
                            ok++;
                        } catch (Exception ex) {
                            failed++;
                            logger.warn("Channel scan: could not open '{}': {}", entry.getImageName(), ex.getMessage());
                        }
                    }
                    final int okF = ok, failedF = failed;
                    final boolean cancelled = cancel.get();
                    Platform.runLater(() -> finishScan(counts, okF, failedF, cancelled));
                },
                "sp-classify-channel-scan");
        t.setDaemon(true);
        t.start();
    }

    private void finishScan(Map<String, Integer> counts, int ok, int failed, boolean cancelled) {
        scanCancel = null;
        scanButton.setText(STRINGS.getString("channelmap.scan"));
        if (!stage.isShowing()) return;
        if (cancelled) {
            scanLabel.setText(STRINGS.getString("channelmap.scan.cancelled"));
            return;
        }
        scanCounts = counts;
        scanImages = ok;
        long partial = counts.values().stream().filter(v -> v < ok).count();
        String msg = String.format(STRINGS.getString("channelmap.scan.done"), ok, counts.size(), partial);
        if (failed > 0) msg += String.format(STRINGS.getString("channelmap.scan.failed"), failed);
        scanLabel.setText(msg);
        rebuildChannelItems();
    }

    // ── Class list cell ─────────────────────────────────────────────────────

    /** Status glyph + class name + hints, coloured by how the row resolves on this image. */
    private final class RowCell extends ListCell<Row> {
        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setTooltip(null);
                setStyle("");
                return;
            }
            Status st = ChannelMappingModel.status(row, imageChannels);
            String glyph =
                    switch (st) {
                        case EXACT -> "✓";
                        case NORMALIZED -> "≈";
                        case FUZZY -> "~";
                        case PARTIAL -> "!";
                        case UNMATCHED -> "✗";
                        case UNMAPPED -> "–";
                    };
            String colour =
                    switch (st) {
                        case EXACT, NORMALIZED -> "#2e7d32";
                        case FUZZY, PARTIAL -> "#e65100";
                        case UNMATCHED -> "#c62828";
                        case UNMAPPED -> "#888888";
                    };
            StringBuilder sb = new StringBuilder(glyph).append("  ").append(row.className());
            int n = row.effectiveNames().size();
            if (n > 0) sb.append("   · ").append(n).append(n == 1 ? " channel" : " channels");
            if (!row.isKnownClass()) sb.append("   ").append(STRINGS.getString("channelmap.orphan.tag"));
            if (!row.hasExplicitChannels() && row.hasMapping()) sb.append("   [CSV]");
            setText(sb.toString());
            setStyle("-fx-text-fill: " + colour + ";" + (row.isKnownClass() ? "" : " -fx-font-style: italic;"));
            setTooltip(new Tooltip(
                    STRINGS.getString("channelmap.status." + st.name().toLowerCase())));
        }
    }
}
