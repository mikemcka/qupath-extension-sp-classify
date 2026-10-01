package qupath.ext.spclassify.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure (JavaFX-free) editing model behind the channel-mapping editor: one {@link Row} per class,
 * built from the project's classes plus whatever a {@link CellTypeTable} already maps, with
 * per-row match status against an image's channels and a lossless conversion back to a table.
 *
 * <p>A row is in one of two modes:
 * <ul>
 *   <li><b>Legacy</b> ({@link Row#hasExplicitChannels()} false) — its display comes from CSV
 *       marker names (or a rule's primary expression), resolved by name matching exactly as
 *       review mode always has. Saving leaves it as it was.</li>
 *   <li><b>Explicit</b> — the user picked exact channel names (or pinned the matches). Saving
 *       writes them as the entry's exact channels.</li>
 * </ul>
 * The first edit to a legacy row seeds its explicit list from what it currently resolves to on
 * the image, so the ticks the user sees are the ticks they edit.
 */
public final class ChannelMappingModel {

    private ChannelMappingModel() {} // utility class

    /** Display-ready summary of a row's state against one image's channels. */
    public enum Status {
        /** No channels and no CSV markers: review leaves the display unchanged for this class. */
        UNMAPPED,
        /** Every name matched a channel verbatim. */
        EXACT,
        /** Every name matched, some only after ignoring case/spacing/punctuation. */
        NORMALIZED,
        /** Every name matched, some only by substring — may show the wrong channel. */
        FUZZY,
        /** Some names matched, some did not. */
        PARTIAL,
        /** None of the names matched this image. */
        UNMATCHED
    }

    /** One class's mapping, mutable while the editor is open. */
    public static final class Row {
        private final String className;
        private final String sourceKey;
        private final boolean known;
        private final List<String> legacyMarkers;
        private final String primary;
        private final String secondary;
        private final String tertiary;
        private List<String> explicitChannels;

        Row(
                String className,
                String sourceKey,
                boolean known,
                List<String> legacyMarkers,
                String primary,
                String secondary,
                String tertiary,
                List<String> explicitChannels) {
            this.className = className;
            this.sourceKey = sourceKey;
            this.known = known;
            this.legacyMarkers = List.copyOf(legacyMarkers);
            this.primary = primary;
            this.secondary = secondary;
            this.tertiary = tertiary;
            this.explicitChannels = explicitChannels == null ? null : new ArrayList<>(explicitChannels);
        }

        /** The class name this row is saved under. */
        public String className() {
            return className;
        }

        /** The table key the row was loaded from (may differ cosmetically from the class), or null. */
        public String sourceKey() {
            return sourceKey;
        }

        /** True when the row's class exists in the project / classifier; false for an orphan. */
        public boolean isKnownClass() {
            return known;
        }

        /** True when the table entry this row came from was keyed under a different spelling. */
        public boolean isRenamed() {
            return sourceKey != null && !sourceKey.equals(className);
        }

        public boolean hasRule() {
            return primary != null || secondary != null || tertiary != null;
        }

        public String primaryExpression() {
            return primary;
        }

        /** CSV / rule-derived display markers (what a legacy row is matched by). */
        public List<String> legacyMarkers() {
            return legacyMarkers;
        }

        public boolean hasExplicitChannels() {
            return explicitChannels != null;
        }

        /** The exact channel list (empty if the row is legacy). */
        public List<String> explicitChannels() {
            return explicitChannels == null ? List.of() : List.copyOf(explicitChannels);
        }

        /** The names review mode will try to match for this row. */
        public List<String> effectiveNames() {
            return explicitChannels != null && !explicitChannels.isEmpty()
                    ? List.copyOf(explicitChannels)
                    : legacyMarkers;
        }

        /** True when names are matched exact-first (explicit channels) rather than as legacy markers. */
        public boolean matchesExactFirst() {
            return explicitChannels != null && !explicitChannels.isEmpty();
        }

        public boolean hasMapping() {
            return !effectiveNames().isEmpty();
        }

        /**
         * Tick or untick {@code channel}. The first edit of a legacy row seeds its explicit list
         * from what it currently resolves to in {@code imageChannels}.
         */
        public void setChannelSelected(String channel, boolean selected, List<String> imageChannels) {
            if (explicitChannels == null) explicitChannels = new ArrayList<>(resolvedChannels(this, imageChannels));
            if (selected) {
                if (!explicitChannels.contains(channel)) explicitChannels.add(channel);
            } else {
                explicitChannels.remove(channel);
            }
        }

        /** Replace the explicit list outright (used by "Clear" and tests). */
        public void setExplicitChannels(List<String> channels) {
            this.explicitChannels = channels == null ? null : new ArrayList<>(channels);
        }

        @Override
        public String toString() {
            return className;
        }
    }

    /**
     * Build one row per class: every name in {@code classNames} (in order, deduplicated), each
     * paired with the table entry it matches (exact key first, else normalized), followed by
     * table entries that matched no class (orphans, kept so nothing is silently dropped).
     */
    public static List<Row> buildRows(CellTypeTable table, Collection<String> classNames) {
        List<Row> rows = new ArrayList<>();
        Set<String> claimed = new LinkedHashSet<>();
        Set<String> seenClasses = new LinkedHashSet<>();

        // Pass 1 claims exact keys so a normalized match can never steal another class's entry.
        List<String> classes = new ArrayList<>();
        if (classNames != null) {
            for (String c : classNames) {
                if (c != null && !c.isBlank() && seenClasses.add(c)) classes.add(c);
            }
        }
        if (table != null) {
            for (String c : classes) {
                if (table.getCellTypes().contains(c)) claimed.add(c);
            }
        }
        for (String c : classes) {
            String key = null;
            if (table != null) {
                if (table.getCellTypes().contains(c)) {
                    key = c;
                } else {
                    for (String k : table.getCellTypes()) {
                        if (!claimed.contains(k)
                                && CellTypeTable.normalizeType(k).equals(CellTypeTable.normalizeType(c))) {
                            key = k;
                            claimed.add(k);
                            break;
                        }
                    }
                }
            }
            rows.add(rowFor(table, c, key, true));
        }
        if (table != null) {
            for (String k : table.getCellTypes()) {
                if (!claimed.contains(k)) rows.add(rowFor(table, k, k, false));
            }
        }
        return rows;
    }

    private static Row rowFor(CellTypeTable table, String className, String key, boolean known) {
        if (table == null || key == null) {
            return new Row(className, null, known, List.of(), null, null, null, null);
        }
        List<String> channels = table.getChannels(key);
        boolean rule = table.hasGatingRules();
        return new Row(
                className,
                key,
                known,
                table.getMarkers(key),
                rule ? table.getPrimaryExpression(key) : null,
                rule ? table.getSecondaryMarkers(key) : null,
                rule ? table.getTertiaryMarkers(key) : null,
                channels.isEmpty() ? null : channels);
    }

    /** The per-name matches of {@code row} against {@code imageChannels}. */
    public static List<ChannelMatcher.Match> matches(Row row, List<String> imageChannels) {
        return ChannelMatcher.matchAll(imageChannels, row.effectiveNames(), row.matchesExactFirst());
    }

    /** The image channels {@code row} would show, in image order. */
    public static List<String> resolvedChannels(Row row, List<String> imageChannels) {
        Set<Integer> idx = new java.util.TreeSet<>();
        for (ChannelMatcher.Match m : matches(row, imageChannels)) idx.addAll(m.indices());
        List<String> out = new ArrayList<>();
        for (int i : idx) out.add(imageChannels.get(i));
        return out;
    }

    /** Summarise how {@code row} resolves against {@code imageChannels}. */
    public static Status status(Row row, List<String> imageChannels) {
        if (!row.hasMapping()) return Status.UNMAPPED;
        List<ChannelMatcher.Match> ms = matches(row, imageChannels);
        long missing = ms.stream().filter(m -> !m.matched()).count();
        if (missing == ms.size()) return Status.UNMATCHED;
        if (missing > 0) return Status.PARTIAL;
        return switch (ChannelMatcher.weakest(ms)) {
            case EXACT -> Status.EXACT;
            case NORMALIZED -> Status.NORMALIZED;
            default -> Status.FUZZY;
        };
    }

    /**
     * Pin a row to exact channel names: the channels it resolves to on this image are added to
     * its explicit list. Explicit names it already had are kept even when this image lacks them
     * or only matches them loosely, so a mapping made on another image is not discarded. For a
     * legacy row, CSV marker names that matched nothing are dropped (and returned).
     *
     * @return the CSV marker names that could not be pinned (empty if all resolved)
     */
    public static List<String> pin(Row row, List<String> imageChannels) {
        List<String> unresolved = new ArrayList<>();
        Set<String> pinned = new LinkedHashSet<>(row.explicitChannels());
        pinned.addAll(resolvedChannels(row, imageChannels));
        if (!row.matchesExactFirst()) {
            for (ChannelMatcher.Match m : matches(row, imageChannels)) {
                if (!m.matched()) unresolved.add(m.query());
            }
        }
        if (!pinned.isEmpty()) row.setExplicitChannels(new ArrayList<>(pinned));
        return unresolved;
    }

    /**
     * Build the table to persist. Rule-format tables stay rule-format with every gating
     * expression untouched; legacy rows are written back exactly as loaded; rows with no mapping
     * and no rule are dropped.
     *
     * @param ruleFormat whether the source table had gating rules
     */
    public static CellTypeTable toTable(List<Row> rows, boolean ruleFormat) {
        CellTypeTable out = new CellTypeTable();
        for (Row r : rows) {
            List<String> explicit = r.explicitChannels();
            if (ruleFormat) {
                if (!r.hasRule() && explicit.isEmpty()) continue;
                out.putRule(r.className(), r.primary, r.secondary, r.tertiary);
                if (!explicit.isEmpty()) out.putChannels(r.className(), explicit);
            } else if (r.hasExplicitChannels()) {
                if (explicit.isEmpty()) continue; // cleared → no mapping for this class
                out.putChannels(r.className(), explicit);
            } else if (!r.legacyMarkers().isEmpty()) {
                out.put(r.className(), r.legacyMarkers());
            }
        }
        return out;
    }
}
