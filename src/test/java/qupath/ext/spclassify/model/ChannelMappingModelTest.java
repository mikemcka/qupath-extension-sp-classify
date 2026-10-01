package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.spclassify.model.ChannelMappingModel.Row;
import qupath.ext.spclassify.model.ChannelMappingModel.Status;

class ChannelMappingModelTest {

    private static final List<String> IMAGE = List.of("DAPI", "CD3", "CD31", "CD4", "CD8", "CD20 (Opal 570)");

    private static Row row(List<Row> rows, String className) {
        return rows.stream()
                .filter(r -> r.className().equals(className))
                .findFirst()
                .orElseThrow();
    }

    // ── buildRows ────────────────────────────────────────────────────────────

    @Test
    void everyProjectClassGetsARowEvenWithoutATable() {
        var rows = ChannelMappingModel.buildRows(null, List.of("CD4T", "CD8T"));
        assertEquals(List.of("CD4T", "CD8T"), rows.stream().map(Row::className).toList());
        assertTrue(rows.stream().allMatch(Row::isKnownClass));
        assertTrue(rows.stream().noneMatch(Row::hasMapping));
    }

    @Test
    void cosmeticallyDifferentCsvKeyMergesIntoTheProjectClass() {
        var t = new CellTypeTable();
        t.put("CD4 T", List.of("CD4", "CD3"));
        var rows = ChannelMappingModel.buildRows(t, List.of("CD4T"));
        assertEquals(1, rows.size());
        Row r = rows.get(0);
        assertEquals("CD4T", r.className());
        assertEquals("CD4 T", r.sourceKey());
        assertTrue(r.isRenamed());
        assertEquals(List.of("CD4", "CD3"), r.legacyMarkers());
    }

    @Test
    void exactKeyIsNotStolenByANormalizedMatch() {
        var t = new CellTypeTable();
        t.put("CD4 T", List.of("CD4"));
        t.put("CD4T", List.of("CD4", "CD3"));
        // "cd4t" is listed first and would normalized-match "CD4 T"; the exact key must still win.
        var rows = ChannelMappingModel.buildRows(t, List.of("cd4t", "CD4T"));
        assertEquals(List.of("CD4", "CD3"), row(rows, "CD4T").legacyMarkers());
        assertEquals("CD4 T", row(rows, "cd4t").sourceKey());
    }

    @Test
    void unmatchedTableEntriesBecomeOrphanRows() {
        var t = new CellTypeTable();
        t.put("Bcell", List.of("CD20"));
        t.put("Ghost", List.of("CD99"));
        var rows = ChannelMappingModel.buildRows(t, List.of("Bcell"));
        assertEquals(
                List.of("Bcell", "Ghost"), rows.stream().map(Row::className).toList());
        assertFalse(row(rows, "Ghost").isKnownClass());
    }

    @Test
    void duplicateAndBlankClassNamesAreIgnored() {
        var rows = ChannelMappingModel.buildRows(null, java.util.Arrays.asList("A", "A", " ", null, "B"));
        assertEquals(List.of("A", "B"), rows.stream().map(Row::className).toList());
    }

    @Test
    void explicitChannelsAreLoadedAsExplicit() {
        var t = new CellTypeTable();
        t.putChannels("CD8T", List.of("CD8", "CD3"));
        Row r = ChannelMappingModel.buildRows(t, List.of("CD8T")).get(0);
        assertTrue(r.hasExplicitChannels());
        assertTrue(r.matchesExactFirst());
        assertEquals(List.of("CD8", "CD3"), r.explicitChannels());
    }

    // ── status ───────────────────────────────────────────────────────────────

    @Test
    void statusReflectsTheWeakestMatch() {
        var t = new CellTypeTable();
        t.put("Exact", List.of("CD3", "CD8"));
        t.put("Norm", List.of("cd-4"));
        t.put("Fuzzy", List.of("CD20"));
        t.put("Partial", List.of("CD3", "FOXP3"));
        t.put("None", List.of("FOXP3"));
        var rows = ChannelMappingModel.buildRows(t, List.of("Exact", "Norm", "Fuzzy", "Partial", "None", "Empty"));
        assertEquals(Status.EXACT, ChannelMappingModel.status(row(rows, "Exact"), IMAGE));
        assertEquals(Status.NORMALIZED, ChannelMappingModel.status(row(rows, "Norm"), IMAGE));
        assertEquals(Status.FUZZY, ChannelMappingModel.status(row(rows, "Fuzzy"), IMAGE));
        assertEquals(Status.PARTIAL, ChannelMappingModel.status(row(rows, "Partial"), IMAGE));
        assertEquals(Status.UNMATCHED, ChannelMappingModel.status(row(rows, "None"), IMAGE));
        assertEquals(Status.UNMAPPED, ChannelMappingModel.status(row(rows, "Empty"), IMAGE));
    }

    @Test
    void resolvedChannelsAreInImageOrder() {
        var t = new CellTypeTable();
        t.put("T", List.of("CD8", "CD3"));
        Row r = ChannelMappingModel.buildRows(t, List.of("T")).get(0);
        assertEquals(List.of("CD3", "CD8"), ChannelMappingModel.resolvedChannels(r, IMAGE));
    }

    // ── editing ──────────────────────────────────────────────────────────────

    @Test
    void firstEditSeedsALegacyRowFromWhatItResolvesTo() {
        var t = new CellTypeTable();
        t.put("T", List.of("cd3", "CD20"));
        Row r = ChannelMappingModel.buildRows(t, List.of("T")).get(0);
        assertFalse(r.hasExplicitChannels());

        r.setChannelSelected("CD4", true, IMAGE);

        assertTrue(r.hasExplicitChannels());
        // The fuzzy/normalized matches are converted to the real channel names, plus the new tick.
        assertEquals(List.of("CD3", "CD20 (Opal 570)", "CD4"), r.explicitChannels());
    }

    @Test
    void untickingRemovesAChannel() {
        Row r = ChannelMappingModel.buildRows(null, List.of("T")).get(0);
        r.setChannelSelected("CD3", true, IMAGE);
        r.setChannelSelected("CD8", true, IMAGE);
        r.setChannelSelected("CD3", false, IMAGE);
        assertEquals(List.of("CD8"), r.explicitChannels());
    }

    @Test
    void pinConvertsMatchesAndReportsUnmatchedMarkers() {
        var t = new CellTypeTable();
        t.put("T", List.of("CD20", "FOXP3"));
        Row r = ChannelMappingModel.buildRows(t, List.of("T")).get(0);
        List<String> unresolved = ChannelMappingModel.pin(r, IMAGE);
        assertEquals(List.of("FOXP3"), unresolved);
        assertEquals(List.of("CD20 (Opal 570)"), r.explicitChannels());
        assertEquals(Status.EXACT, ChannelMappingModel.status(r, IMAGE));
    }

    @Test
    void pinKeepsExplicitChannelsMissingFromThisImage() {
        var t = new CellTypeTable();
        t.putChannels("T", List.of("CD3", "PD-1 (other panel)"));
        Row r = ChannelMappingModel.buildRows(t, List.of("T")).get(0);
        assertTrue(ChannelMappingModel.pin(r, IMAGE).isEmpty());
        assertEquals(List.of("CD3", "PD-1 (other panel)"), r.explicitChannels());
    }

    @Test
    void pinLeavesAFullyUnmatchedLegacyRowAlone() {
        var t = new CellTypeTable();
        t.put("T", List.of("FOXP3"));
        Row r = ChannelMappingModel.buildRows(t, List.of("T")).get(0);
        assertEquals(List.of("FOXP3"), ChannelMappingModel.pin(r, IMAGE));
        assertFalse(r.hasExplicitChannels());
        assertEquals(List.of("FOXP3"), r.legacyMarkers());
    }

    // ── toTable ──────────────────────────────────────────────────────────────

    @Test
    void untouchedLegacyRowsRoundTripUnchanged() {
        var t = new CellTypeTable();
        t.put("T", List.of("CD3", "CD4"));
        t.put("Ghost", List.of("CD99"));
        var out = ChannelMappingModel.toTable(ChannelMappingModel.buildRows(t, List.of("T", "B")), false);
        assertEquals(List.of("T", "Ghost"), List.copyOf(out.getCellTypes()));
        assertEquals(List.of("CD3", "CD4"), out.getMarkers("T"));
        assertFalse(out.hasChannels("T"));
        assertEquals(List.of("CD99"), out.getMarkers("Ghost"));
    }

    @Test
    void explicitRowsAreSavedUnderTheProjectClassName() {
        var t = new CellTypeTable();
        t.put("CD4 T", List.of("CD4"));
        var rows = ChannelMappingModel.buildRows(t, List.of("CD4T"));
        rows.get(0).setChannelSelected("CD3", true, IMAGE);
        var out = ChannelMappingModel.toTable(rows, false);
        assertEquals(List.of("CD4T"), List.copyOf(out.getCellTypes()));
        assertEquals(List.of("CD4", "CD3"), out.getChannels("CD4T"));
        // Simple format: the channels are also the display markers, so CSV export still works.
        assertEquals(List.of("CD4", "CD3"), out.getMarkers("CD4T"));
    }

    @Test
    void clearedAndUnmappedRowsAreDropped() {
        var t = new CellTypeTable();
        t.put("T", List.of("CD3"));
        var rows = ChannelMappingModel.buildRows(t, List.of("T", "Unmapped"));
        row(rows, "T").setExplicitChannels(List.of());
        assertTrue(ChannelMappingModel.toTable(rows, false).isEmpty());
    }

    @Test
    void ruleFormatKeepsEveryExpressionAndAddsChannels() {
        var t = new CellTypeTable();
        t.putRule("CD8T", "CD8&CD3", "CD45", "CD103|CD45RA");
        t.putRule("Plasma", "CD38&!IgA", null, "VIM");
        var rows = ChannelMappingModel.buildRows(t, List.of("CD8T", "Plasma", "NewClass"));
        row(rows, "CD8T").setChannelSelected("CD31", true, IMAGE);
        row(rows, "NewClass").setChannelSelected("CD20 (Opal 570)", true, IMAGE);

        var out = ChannelMappingModel.toTable(rows, true);

        assertTrue(out.hasGatingRules());
        assertEquals("CD8&CD3", out.getPrimaryExpression("CD8T"));
        assertEquals("CD45", out.getSecondaryMarkers("CD8T"));
        assertEquals("CD103|CD45RA", out.getTertiaryMarkers("CD8T"));
        // Seeded from the rule-derived markers in image order, then the new tick.
        assertEquals(List.of("CD3", "CD8", "CD31"), out.getChannels("CD8T"));
        assertEquals("CD38&!IgA", out.getPrimaryExpression("Plasma"));
        assertFalse(out.hasChannels("Plasma"));
        assertNull(out.getPrimaryExpression("NewClass"));
        assertEquals(List.of("CD20 (Opal 570)"), out.getChannels("NewClass"));
    }

    @Test
    void clearingARuleRowKeepsTheRule() {
        var t = new CellTypeTable();
        t.putRule("CD8T", "CD8&CD3", null, null);
        t.putChannels("CD8T", List.of("CD8"));
        var rows = ChannelMappingModel.buildRows(t, List.of("CD8T"));
        rows.get(0).setExplicitChannels(List.of());
        var out = ChannelMappingModel.toTable(rows, true);
        assertEquals("CD8&CD3", out.getPrimaryExpression("CD8T"));
        assertFalse(out.hasChannels("CD8T"));
    }

    @Test
    void uncappedChannelListsSurvive() {
        Row r = ChannelMappingModel.buildRows(null, List.of("Big")).get(0);
        List<String> many = List.of("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9");
        r.setExplicitChannels(many);
        assertEquals(many, ChannelMappingModel.toTable(List.of(r), false).getChannels("Big"));
    }
}
