package qupath.ext.spclassify.model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps a cell-type name to marker channel names and optional gating rules.
 * <p>
 * Supports two CSV formats, auto-detected from the header:
 * <p>
 * <b>Simple format</b> (channel display only):
 * <pre>
 * CellType,Marker1,Marker2,Marker3
 * T-Cell,CD3,,
 * Macrophage,CD68,CD163,
 * </pre>
 *
 * <b>Rule format</b> (gating rules — matching Python CellTune):
 * <pre>
 * CellType,PrimaryMarker,SecondaryMarker,TertiaryMarker
 * CD8T,CD8,CD3,CD103|CD45|CD45RA
 * Plasma_CD38,CD38&amp;!IgA,,CD45|VIM
 * Macrophage,CD68|CD163|CD206,,CD14|CD38|VIM
 * </pre>
 * Additional columns (hex, Channel_1/2/3) are accepted and ignored. A rule-format table may
 * also carry an optional {@code DisplayChannels} column (pipe-separated exact channel names,
 * written by the channel-mapping editor).
 * <p>
 * When gating rules are present ({@link #hasGatingRules()} returns true),
 * marker channels for display are derived from the primary expression's
 * must-have and or-expression markers.
 * <p>
 * <b>Exact channels.</b> Independently of either format, a cell type may hold an explicit list
 * of image channel names chosen in the channel-mapping editor ({@link #putChannels}). These are
 * matched verbatim first by auto channel-switching, and take precedence over the CSV-derived
 * display markers. For a simple-format entry they also become its display markers, so the
 * table still exports as an ordinary {@code CellType,Marker1,...} CSV.
 * <p>
 * Fields are parsed as RFC-4180 CSV (double-quoted fields may contain commas), and a leading
 * UTF-8 byte-order mark (as written by Excel) is ignored.
 */
public class CellTypeTable {

    /**
     * Number of marker columns the legacy CSV / JSON formats were written with. Display markers
     * are no longer truncated to this; it remains the minimum width of an exported simple-format
     * CSV (so tables of up to this many markers export byte-identically to earlier versions),
     * the number of leading CSV columns always read as markers, and the cap on markers derived
     * from a rule's primary expression.
     */
    public static final int MAX_MARKERS = 5;

    private static final Logger logger = LoggerFactory.getLogger(CellTypeTable.class);

    private final Map<String, List<String>> table; // cellType → display markers
    private final Map<String, String> primaryMarkers; // cellType → primary expression
    private final Map<String, String> secondaryMarkers; // cellType → pipe-separated
    private final Map<String, String> tertiaryMarkers; // cellType → pipe-separated
    private final Map<String, List<String>> exactChannels; // cellType → editor-chosen channel names
    // normalized(cellType) → actual key, for tolerant lookup (see getMarkers). The
    // predicted class name and the CSV CellType are typed independently, so they can
    // differ in case/spacing/punctuation; this lets auto channel-switch still resolve.
    private final Map<String, String> normalizedIndex;
    private boolean hasRules = false;

    public CellTypeTable() {
        this.table = new LinkedHashMap<>();
        this.primaryMarkers = new LinkedHashMap<>();
        this.secondaryMarkers = new LinkedHashMap<>();
        this.tertiaryMarkers = new LinkedHashMap<>();
        this.exactChannels = new LinkedHashMap<>();
        this.normalizedIndex = new LinkedHashMap<>();
    }

    /**
     * Normalize a cell-type name to lowercase alphanumeric characters only, so lookups
     * tolerate case, spacing, and punctuation differences between the classifier's class
     * names and the CSV {@code CellType} column. Mirrors the channel-name normalization in
     * {@code ChannelSelector} so both sides of auto channel-switching match the same way.
     */
    static String normalizeType(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /** Register {@code cellType} in the normalized index, warning on a normalized collision. */
    private void indexKey(String cellType) {
        String norm = normalizeType(cellType);
        if (norm.isEmpty()) return;
        String existing = normalizedIndex.putIfAbsent(norm, cellType);
        if (existing != null && !existing.equals(cellType)) {
            logger.warn(
                    "Cell types '{}' and '{}' normalize to the same key; auto channel-switch "
                            + "lookups will resolve to '{}'.",
                    cellType,
                    existing,
                    existing);
        }
    }

    // ── Simple format API (display markers) ─────────────────────────────────

    /**
     * Define a cell type with its display marker channels. Blank entries are dropped; the list
     * is not truncated.
     */
    public void put(String cellType, List<String> markers) {
        table.put(cellType, Collections.unmodifiableList(cleanNames(markers)));
        indexKey(cellType);
    }

    /** Strip each name, drop blanks and duplicates, keep order. */
    private static List<String> cleanNames(List<String> names) {
        Set<String> out = new LinkedHashSet<>();
        if (names != null) {
            for (String m : names) {
                if (m != null && !m.isBlank()) out.add(m.strip());
            }
        }
        return new ArrayList<>(out);
    }

    /**
     * The actual key for {@code cellType}: itself if present, else the key it matches after
     * case/spacing/punctuation normalization, else {@code null}.
     */
    public String resolveKey(String cellType) {
        if (cellType == null) return null;
        if (table.containsKey(cellType)) return cellType;
        return normalizedIndex.get(normalizeType(cellType));
    }

    /**
     * @return marker channels for the given cell type, or an empty list.
     *     <p>Tries an exact key match first; if none, falls back to a normalized
     *     (case/spacing/punctuation-insensitive) match so a predicted class name that
     *     differs only cosmetically from the CSV {@code CellType} still resolves.
     */
    public List<String> getMarkers(String cellType) {
        if (cellType == null) return Collections.emptyList();
        List<String> exact = table.get(cellType);
        if (exact != null) return exact;
        String actual = normalizedIndex.get(normalizeType(cellType));
        return actual == null ? Collections.emptyList() : table.getOrDefault(actual, Collections.emptyList());
    }

    // ── Exact channel API (channel-mapping editor) ──────────────────────────

    /**
     * Set the exact image channel names to display for {@code cellType}, as chosen in the
     * channel-mapping editor. An empty/null list removes them (the entry then falls back to its
     * CSV-derived display markers).
     * <p>
     * For a simple-format table the channels also become the entry's display markers. For a
     * rule-format table the gating expressions are left untouched; a cell type with no rule yet
     * is added with an empty rule so the table stays in rule format.
     */
    public void putChannels(String cellType, List<String> channels) {
        List<String> clean = cleanNames(channels);
        if (clean.isEmpty()) {
            exactChannels.remove(cellType);
            return;
        }
        if (hasRules) {
            if (!table.containsKey(cellType)) putRule(cellType, null, null, null);
        } else {
            put(cellType, clean);
        }
        exactChannels.put(cellType, Collections.unmodifiableList(clean));
        indexKey(cellType);
    }

    /**
     * @return the editor-chosen exact channel names for {@code cellType} (same tolerant lookup as
     *     {@link #getMarkers}), or an empty list if none were set.
     */
    public List<String> getChannels(String cellType) {
        String key = resolveKey(cellType);
        return key == null ? Collections.emptyList() : exactChannels.getOrDefault(key, Collections.emptyList());
    }

    /** @return true if {@code cellType} has editor-chosen exact channel names */
    public boolean hasChannels(String cellType) {
        return !getChannels(cellType).isEmpty();
    }

    /** @return true if any cell type has editor-chosen exact channel names */
    public boolean hasAnyChannels() {
        return !exactChannels.isEmpty();
    }

    /** @return all cell type names in insertion order */
    public Set<String> getCellTypes() {
        return Collections.unmodifiableSet(table.keySet());
    }

    /** @return number of defined cell types */
    public int size() {
        return table.size();
    }

    /** @return true if the table contains no entries */
    public boolean isEmpty() {
        return table.isEmpty();
    }

    // ── Gating rule API ─────────────────────────────────────────────────────

    /**
     * Define a cell type with gating rules (rule format).
     */
    public void putRule(String cellType, String primaryExpr, String secondary, String tertiary) {
        this.hasRules = true;
        primaryMarkers.put(cellType, primaryExpr);
        secondaryMarkers.put(cellType, secondary);
        tertiaryMarkers.put(cellType, tertiary);

        // Derive display markers from primary expression
        List<String> displayMarkers = new ArrayList<>();
        if (primaryExpr != null && !primaryExpr.isBlank()) {
            // Extract simple marker names (strip operators and parens)
            String[] tokens = primaryExpr.split("[&|!()]+");
            for (String t : tokens) {
                t = t.strip();
                if (!t.isEmpty() && displayMarkers.size() < MAX_MARKERS) {
                    displayMarkers.add(t);
                }
            }
        }
        table.put(cellType, Collections.unmodifiableList(displayMarkers));
        indexKey(cellType);
    }

    /** @return true if this table was loaded from a rule-format CSV */
    public boolean hasGatingRules() {
        return hasRules;
    }

    /** @return primary marker expression for the given cell type, or null */
    public String getPrimaryExpression(String cellType) {
        return primaryMarkers.get(cellType);
    }

    /** @return secondary markers (pipe-separated) for the given cell type, or null */
    public String getSecondaryMarkers(String cellType) {
        return secondaryMarkers.get(cellType);
    }

    /** @return tertiary markers (pipe-separated) for the given cell type, or null */
    public String getTertiaryMarkers(String cellType) {
        return tertiaryMarkers.get(cellType);
    }

    /**
     * Collect all unique channel names referenced across all rules
     * (primary, secondary, tertiary expressions).
     *
     * @return sorted list of all referenced channel names
     */
    public List<String> getAllRuleChannels() {
        Set<String> channels = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (String cellType : table.keySet()) {
            addChannelsFrom(primaryMarkers.get(cellType), channels);
            addChannelsFrom(secondaryMarkers.get(cellType), channels);
            addChannelsFrom(tertiaryMarkers.get(cellType), channels);
        }
        return new ArrayList<>(channels);
    }

    private void addChannelsFrom(String expr, Set<String> channels) {
        if (expr == null || expr.isBlank()) return;
        for (String token : expr.split("[&|!()]+")) {
            token = token.strip();
            if (!token.isEmpty()) channels.add(token);
        }
    }

    // ── CSV I/O ─────────────────────────────────────────────────────────────

    /**
     * Load a CellTypeTable from a CSV file.
     * Auto-detects simple vs rule format from the header.
     */
    public static CellTypeTable loadFromCSV(Path path) throws IOException {
        CellTypeTable tbl = new CellTypeTable();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) return tbl;
            // Excel's "CSV UTF-8" writes a byte-order mark, which would otherwise glue itself
            // to the first header cell and break rule-format column detection.
            if (header.startsWith("\uFEFF")) header = header.substring(1);

            // Detect format from header
            String headerLower = header.toLowerCase();
            boolean isRuleFormat = headerLower.contains("primarymarker");

            if (isRuleFormat) {
                return loadRuleFormat(tbl, header, reader);
            } else {
                return loadSimpleFormat(tbl, header, reader);
            }
        }
    }

    private static CellTypeTable loadSimpleFormat(CellTypeTable tbl, String header, BufferedReader reader)
            throws IOException {
        // The first MAX_MARKERS columns after CellType are always markers (the historical
        // behaviour, whatever the header says). Further columns are markers only when their
        // header names them as such (Marker6, Marker7, ...), so a trailing hex/colour/notes
        // column is not mistaken for a channel.
        String[] cols = parseCsvLine(header);
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty()) continue;

            String[] parts = parseCsvLine(line);
            if (parts.length < 1) continue;

            String cellType = parts[0].strip();
            if (cellType.isEmpty()) continue;

            List<String> markers = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                boolean markerColumn = i <= MAX_MARKERS
                        || (i < cols.length && cols[i].strip().toLowerCase().startsWith("marker"));
                if (markerColumn) markers.add(parts[i].strip());
            }
            tbl.put(cellType, markers);
        }
        return tbl;
    }

    private static CellTypeTable loadRuleFormat(CellTypeTable tbl, String header, BufferedReader reader)
            throws IOException {
        // Parse header to find column indices
        String[] cols = parseCsvLine(header);
        int classCol = -1, primaryCol = -1, secondaryCol = -1, tertiaryCol = -1, channelsCol = -1;
        for (int i = 0; i < cols.length; i++) {
            String col = cols[i].strip().toLowerCase();
            if (col.equals("celltype") || col.equals("class")) classCol = i;
            else if (col.equals("primarymarker")) primaryCol = i;
            else if (col.equals("secondarymarker")) secondaryCol = i;
            else if (col.equals("tertiarymarker")) tertiaryCol = i;
            else if (col.equals("displaychannels")) channelsCol = i;
        }

        if (classCol < 0) {
            throw new IOException("Rule-format CSV must have a 'CellType' or 'class' column");
        }
        if (primaryCol < 0) {
            throw new IOException("Rule-format CSV must have a 'PrimaryMarker' column");
        }

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.strip();
            if (line.isEmpty()) continue;

            String[] parts = parseCsvLine(line);
            if (parts.length <= classCol) continue;

            String cellType = parts[classCol].strip();
            if (cellType.isEmpty()) continue;

            String primary = safeGet(parts, primaryCol);
            String secondary = safeGet(parts, secondaryCol);
            String tertiary = safeGet(parts, tertiaryCol);

            tbl.putRule(cellType, primary, secondary, tertiary);
            String channels = safeGet(parts, channelsCol);
            if (channels != null) tbl.putChannels(cellType, Arrays.asList(channels.split("\\|")));
        }
        return tbl;
    }

    /**
     * Split one CSV line into fields (RFC 4180): a field wrapped in double quotes may contain
     * commas, and {@code ""} inside it is a literal quote. An unquoted line splits exactly as
     * {@code line.split(",", -1)} did, so existing tables parse identically.
     */
    static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == '"' && cur.toString().isBlank()) {
                inQuotes = true;
                cur.setLength(0);
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }

    /** Quote a CSV field only if it contains a comma, quote, or newline. */
    private static String csvField(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String safeGet(String[] parts, int idx) {
        if (idx < 0 || idx >= parts.length) return null;
        String val = parts[idx].strip();
        return val.isEmpty() ? null : val;
    }

    /**
     * Save this table to a CSV file.
     * Uses rule format if gating rules are present, otherwise simple format. A simple table is
     * written with {@code max(MAX_MARKERS, longest marker list)} marker columns; a rule table
     * gains a {@code DisplayChannels} column only when some entry has exact channels. Tables
     * without either extension export exactly as before.
     */
    public void saveToCSV(Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            if (hasRules) {
                boolean withChannels = hasAnyChannels();
                writer.write("CellType,PrimaryMarker,SecondaryMarker,TertiaryMarker");
                if (withChannels) writer.write(",DisplayChannels");
                writer.newLine();
                for (String cellType : table.keySet()) {
                    writer.write(csvField(cellType));
                    writer.write(',');
                    writer.write(csvField(Objects.toString(primaryMarkers.get(cellType), "")));
                    writer.write(',');
                    writer.write(csvField(Objects.toString(secondaryMarkers.get(cellType), "")));
                    writer.write(',');
                    writer.write(csvField(Objects.toString(tertiaryMarkers.get(cellType), "")));
                    if (withChannels) {
                        writer.write(',');
                        writer.write(csvField(String.join("|", exactChannels.getOrDefault(cellType, List.of()))));
                    }
                    writer.newLine();
                }
            } else {
                int width = MAX_MARKERS;
                for (List<String> markers : table.values()) width = Math.max(width, markers.size());
                StringBuilder header = new StringBuilder("CellType");
                for (int i = 1; i <= width; i++) {
                    header.append(",Marker").append(i);
                }
                writer.write(header.toString());
                writer.newLine();
                for (var entry : table.entrySet()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(csvField(entry.getKey()));
                    List<String> markers = entry.getValue();
                    for (int i = 0; i < width; i++) {
                        sb.append(',');
                        if (i < markers.size()) {
                            sb.append(csvField(markers.get(i)));
                        }
                    }
                    writer.write(sb.toString());
                    writer.newLine();
                }
            }
        }
    }

    @Override
    public String toString() {
        return "CellTypeTable[" + table.size() + " types" + (hasRules ? ", with gating rules" : "")
                + (exactChannels.isEmpty() ? "" : ", " + exactChannels.size() + " with exact channels") + "]";
    }
}
