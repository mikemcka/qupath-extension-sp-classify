package qupath.ext.spclassify.ui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.model.CellPrediction;
import qupath.ext.spclassify.model.CellTypeTable;
import qupath.ext.spclassify.model.ChannelMatcher;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * Optional auto-channel switching during review.
 *
 * <p>When enabled and a {@link CellTypeTable} is loaded, this sets the channel visibility based
 * on the predicted cell type's channels each time the reviewer advances to a new cell. A class's
 * exact channels (chosen in the channel-mapping editor) are matched verbatim first; otherwise its
 * CSV marker names are matched by name exactly as they always have been.
 *
 * <p>The table is looked up through a supplier on every cell rather than captured once, so a
 * mapping edited while the review window is open takes effect on the next cell (or immediately,
 * via the editor's save callback).
 *
 * <p>When disabled — or when no CellTypeTable is loaded — the viewer's channel settings are left
 * untouched (the user switches manually).
 */
public class ChannelSelector {

    private static final Logger logger = LoggerFactory.getLogger(ChannelSelector.class);

    private static final ResourceBundle STRINGS = ResourceBundle.getBundle("qupath.ext.spclassify.ui.strings");

    private final QuPathGUI qupath;
    private final Supplier<CellTypeTable> tableSupplier;
    private final CheckBox autoSwitchCheckBox;
    private final CheckBox autoDisplayRangeCheckBox;
    private final Label statusLabel = new Label();

    /**
     * @param qupath        the QuPath instance
     * @param cellTypeTable the cell-type → marker mapping (may be null); fixed for this selector's life
     */
    public ChannelSelector(QuPathGUI qupath, CellTypeTable cellTypeTable) {
        this(qupath, () -> cellTypeTable);
    }

    /**
     * @param qupath        the QuPath instance
     * @param tableSupplier returns the current mapping (may return null); consulted on every cell
     */
    public ChannelSelector(QuPathGUI qupath, Supplier<CellTypeTable> tableSupplier) {
        this.qupath = qupath;
        this.tableSupplier = tableSupplier == null ? () -> null : tableSupplier;
        this.autoSwitchCheckBox = new CheckBox(STRINGS.getString("sample.autochannel.label"));
        // Both checkboxes remember their state across review windows and QuPath restarts.
        this.autoSwitchCheckBox.selectedProperty().bindBidirectional(Prefs.AUTO_SWITCH);

        // Sub-option: whether newly-shown channels also get their brightness/contrast
        // (display range) auto-adjusted. Off by default so the user's existing display
        // settings are preserved; only channel visibility changes unless this is ticked.
        this.autoDisplayRangeCheckBox = new CheckBox(STRINGS.getString("sample.autochannel.displayrange.label"));
        this.autoDisplayRangeCheckBox.selectedProperty().bindBidirectional(Prefs.AUTO_DISPLAY_RANGE);
        // The display-range adjustment only happens as part of channel switching, so grey
        // it out when auto-switching itself is disabled.
        this.autoDisplayRangeCheckBox
                .disableProperty()
                .bind(autoSwitchCheckBox.selectedProperty().not());

        statusLabel.setStyle(
                "-fx-font-size: 11px;"); // colour via setTextFill (an inline -fx-text-fill would override it)
        statusLabel.setMinWidth(0);
        statusLabel.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setEllipsisString("…");
        autoSwitchCheckBox.selectedProperty().addListener((obs, was, on) -> {
            if (!on) setStatus("", null);
        });
    }

    /**
     * Persistent review-display preferences. A holder class so the pure static matching helpers
     * (and their unit tests) never touch QuPath's preference store.
     */
    private static final class Prefs {
        static final BooleanProperty AUTO_SWITCH =
                PathPrefs.createPersistentPreference("celltune.review.autoChannel", true);
        static final BooleanProperty AUTO_DISPLAY_RANGE =
                PathPrefs.createPersistentPreference("celltune.review.autoDisplayRange", false);
    }

    /** @return the checkbox that gates auto-switching; add it to your UI */
    public CheckBox getCheckBox() {
        return autoSwitchCheckBox;
    }

    /**
     * @return the checkbox that gates auto-adjusting brightness/contrast (display
     *     range) of shown channels; add it to your UI. Off by default.
     */
    public CheckBox getDisplayRangeCheckBox() {
        return autoDisplayRangeCheckBox;
    }

    /** @return a one-line label describing what the last switch showed (or why it did nothing) */
    public Label getStatusLabel() {
        return statusLabel;
    }

    /**
     * The review window's channel controls: both checkboxes, the status line, and (when
     * {@code onEditMapping} is non-null) an "Edit channel mapping…" button that receives the
     * window the button lives in, so the editor can be owned by — and stay above — it.
     */
    public Node buildControls(Consumer<Window> onEditMapping) {
        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);
        statusRow.getChildren().add(statusLabel);
        if (onEditMapping != null) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.SOMETIMES);
            Button edit = new Button(STRINGS.getString("channelmap.edit.button"));
            edit.setTooltip(new Tooltip(STRINGS.getString("channelmap.edit.tooltip")));
            edit.setOnAction(e -> onEditMapping.accept(
                    edit.getScene() == null ? null : edit.getScene().getWindow()));
            statusRow.getChildren().addAll(spacer, edit);
        }
        return new VBox(4, autoSwitchCheckBox, autoDisplayRangeCheckBox, statusRow);
    }

    /**
     * Apply channel switching for the current cell in the given review controller.
     * <p>
     * Does nothing if the checkbox is unchecked, if no CellTypeTable is loaded,
     * or if the predicted cell type has no associated markers.
     */
    public void applyForCurrentCell(ReviewController controller) {
        if (!autoSwitchCheckBox.isSelected()) return;
        CellTypeTable cellTypeTable = tableSupplier.get();
        if (cellTypeTable == null || cellTypeTable.isEmpty()) {
            setStatus(STRINGS.getString("channelmap.status.notable"), null);
            return;
        }

        CellPrediction pred = controller.getCurrentPrediction();
        if (pred == null) return;

        String predictedType = pred.avgLabel();
        if (predictedType == null) return;

        List<String> channels = cellTypeTable.getChannels(predictedType);
        boolean exactFirst = !channels.isEmpty();
        List<String> names = exactFirst ? channels : cellTypeTable.getMarkers(predictedType);
        if (names == null || names.isEmpty()) {
            setStatus(String.format(STRINGS.getString("channelmap.status.unmapped.class"), predictedType), null);
            return;
        }

        List<String> shown = applyToViewer(qupath, names, exactFirst, autoDisplayRangeCheckBox.isSelected());
        if (shown == null) return; // no viewer / display
        if (shown.isEmpty()) {
            setStatus(
                    String.format(STRINGS.getString("channelmap.status.nomatch.class"), predictedType),
                    String.join(", ", names));
        } else {
            setStatus(
                    String.format(
                            STRINGS.getString("channelmap.status.showing"), predictedType, String.join(", ", shown)),
                    null);
        }
    }

    private void setStatus(String text, String tooltipDetail) {
        statusLabel.setText(text);
        statusLabel.setTextFill(
                tooltipDetail == null ? javafx.scene.paint.Color.web("#555") : javafx.scene.paint.Color.web("#c62828"));
        statusLabel.setTooltip(
                text == null || text.isBlank()
                        ? null
                        : new Tooltip(tooltipDetail == null ? text : text + "\n" + tooltipDetail));
    }

    /**
     * Show only the channels {@code names} resolve to in the current viewer, hiding all others.
     * Shared by review auto-switching and the mapping editor's Preview.
     *
     * @param exactFirst true for editor-chosen exact channel names, false for legacy CSV markers
     * @return the (original) names of the channels now shown — empty if nothing matched, in which
     *     case the display is left untouched — or {@code null} if there is no viewer/display
     */
    static List<String> applyToViewer(QuPathGUI qupath, List<String> names, boolean exactFirst, boolean autoRange) {
        var viewer = qupath == null ? null : qupath.getViewer();
        if (viewer == null) return null;

        try {
            var display = viewer.getImageDisplay();
            if (display == null) return null;

            var channels = display.availableChannels();
            if (channels == null) return null;

            List<String> originalNames = new ArrayList<>(channels.size());
            List<String> displayNames = new ArrayList<>(channels.size());
            for (ChannelDisplayInfo ch : channels) {
                originalNames.add(originalName(ch));
                displayNames.add(ch.getName());
            }
            Set<Integer> showIdx = resolveIndices(originalNames, displayNames, names, exactFirst);

            logger.info("Auto-switch: {} matched {} of {} channels", names, showIdx.size(), channels.size());
            if (showIdx.isEmpty()) {
                // No channel matched any marker — leave the display untouched rather than
                // blanking every channel, which would look like the feature "did nothing".
                logger.warn(
                        "Auto-switch: no channel matched {} (channels: {}); leaving display unchanged",
                        names,
                        originalNames);
                return List.of();
            }

            List<String> shown = new ArrayList<>();
            int i = 0;
            for (var ch : channels) {
                boolean shouldShow = showIdx.contains(i);
                display.setChannelSelected(ch, shouldShow);
                if (shouldShow) {
                    shown.add(originalNames.get(i));
                    if (autoRange) display.autoSetDisplayRange(ch);
                }
                i++;
            }

            // Force the viewer to repaint with the updated channel visibility
            viewer.repaintEntireImage();

            logger.debug("Auto-switched channels to: {}", shown);
            return shown;
        } catch (Exception e) {
            // If the display API is unavailable or throws, fall back silently
            logger.warn("Could not auto-switch channels: {}", e.getMessage());
            return null;
        }
    }

    /**
     * The image's own name for a display channel. QuPath's display name for a server channel
     * appends {@code " (C<n>)"} ({@code "CD3"} → {@code "CD3 (C4)"}), which defeats exact
     * matching and lets {@code "CD3"} substring-match {@code "CD31 (C5)"}; the original name
     * is what users type and what the editor stores.
     */
    static String originalName(ChannelDisplayInfo ch) {
        if (ch instanceof DirectServerChannelInfo direct) {
            String n = direct.getOriginalChannelName();
            if (n != null && !n.isBlank()) return n;
        }
        return ch.getName();
    }

    /**
     * Decide which channels to show. Each name is resolved against the channels' original names
     * ({@link ChannelMatcher#matchChannel} when {@code exactFirst}, else the legacy
     * {@link ChannelMatcher#matchMarker}); a name that matches nothing there is retried against
     * the display names (the strings matching used before), so a table written against those
     * still works. Pure and side-effect free for unit testing.
     *
     * @param originalNames channel names as the image defines them, in display order
     * @param displayNames  the viewer's display names for the same channels (may be null)
     * @param names         channel / marker names to show
     * @return 0-based indices of channels to show
     */
    static Set<Integer> resolveIndices(
            List<String> originalNames, List<String> displayNames, List<String> names, boolean exactFirst) {
        Set<Integer> show = new LinkedHashSet<>();
        if (originalNames == null || names == null) return show;
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            ChannelMatcher.Match m = exactFirst
                    ? ChannelMatcher.matchChannel(originalNames, name)
                    : ChannelMatcher.matchMarker(originalNames, name);
            if (!m.matched() && displayNames != null) m = ChannelMatcher.matchMarker(displayNames, name);
            show.addAll(m.indices());
        }
        return show;
    }

    /**
     * Decide which channel indices to show for the given markers. Pure and side-effect free
     * so it can be unit-tested without a viewer.
     *
     * <p>Matching is <b>exact-preferred</b>: for each marker, if any channel's normalized
     * name equals the normalized marker, only those exact channels are chosen. Substring
     * matching (channel contains marker, or vice-versa) is used only as a fallback for a
     * marker with no exact channel — so marker {@code "CD3"} does not also light up
     * {@code "CD31"}/{@code "CD34"} when a real {@code "CD3"} channel is present, while a
     * fluorophore-tagged channel like {@code "CD3 (Opal 570)"} is still matched when no
     * bare {@code "CD3"} channel exists.
     *
     * @param channelNames available channel names, in display order
     * @param markerNames  marker names to show
     * @return 0-based indices into {@code channelNames} that should be shown
     */
    static Set<Integer> selectChannelIndices(List<String> channelNames, List<String> markerNames) {
        return resolveIndices(channelNames, null, markerNames, false);
    }
}
