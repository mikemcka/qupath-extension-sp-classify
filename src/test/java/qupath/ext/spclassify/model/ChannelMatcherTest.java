package qupath.ext.spclassify.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import qupath.ext.spclassify.model.ChannelMatcher.Kind;

class ChannelMatcherTest {

    private static final List<String> CHANNELS = List.of("DAPI", "CD3", "CD31", "CD8_S1 - Cy5", "CD20 (Opal 570)");

    @Test
    void verbatimNameIsExact() {
        var m = ChannelMatcher.matchMarker(CHANNELS, "CD3");
        assertEquals(Kind.EXACT, m.kind());
        assertEquals(List.of(1), m.indices());
    }

    @Test
    void cosmeticDifferenceIsNormalized() {
        var m = ChannelMatcher.matchMarker(CHANNELS, "cd8_s1-cy5");
        assertEquals(Kind.NORMALIZED, m.kind());
        assertEquals(List.of(3), m.indices());
    }

    @Test
    void substringOnlyIsFuzzy() {
        var m = ChannelMatcher.matchMarker(CHANNELS, "CD20");
        assertEquals(Kind.FUZZY, m.kind());
        assertEquals(List.of(4), m.indices());
    }

    @Test
    void fuzzyCanBeAmbiguous() {
        // No bare CD3 channel: the substring fallback picks up every CD3x channel.
        var m = ChannelMatcher.matchMarker(List.of("CD31", "CD34", "DAPI"), "CD3");
        assertEquals(Kind.FUZZY, m.kind());
        assertEquals(List.of(0, 1), m.indices());
    }

    @Test
    void noMatchIsNone() {
        var m = ChannelMatcher.matchMarker(CHANNELS, "FOXP3");
        assertEquals(Kind.NONE, m.kind());
        assertTrue(m.indices().isEmpty());
        assertFalse(m.matched());
    }

    @Test
    void blankOrNullIsNone() {
        assertEquals(Kind.NONE, ChannelMatcher.matchMarker(CHANNELS, "  ").kind());
        assertEquals(Kind.NONE, ChannelMatcher.matchMarker(null, "CD3").kind());
        assertEquals(Kind.NONE, ChannelMatcher.matchChannel(CHANNELS, null).kind());
    }

    @Test
    void legacyMarkerMatchingKeepsEveryNormalizedEqualChannel() {
        // Historical behaviour: all normalized-equal channels are shown, even if one is verbatim.
        var m = ChannelMatcher.matchMarker(List.of("CD3", "cd3", "CD 3"), "CD3");
        assertEquals(Kind.EXACT, m.kind());
        assertEquals(List.of(0, 1, 2), m.indices());
    }

    @Test
    void exactFirstSelectsOnlyTheVerbatimChannel() {
        var m = ChannelMatcher.matchChannel(List.of("CD3", "cd3", "CD 3"), "CD3");
        assertEquals(Kind.EXACT, m.kind());
        assertEquals(List.of(0), m.indices());
    }

    @Test
    void exactFirstFallsBackToMarkerMatching() {
        // A mapping made on one image still resolves on another with cosmetically different names.
        var m = ChannelMatcher.matchChannel(List.of("DAPI", "CD8 S1 Cy5"), "CD8_S1 - Cy5");
        assertEquals(Kind.NORMALIZED, m.kind());
        assertEquals(List.of(1), m.indices());
    }

    @Test
    void exactMatchIgnoresSurroundingWhitespace() {
        assertEquals(
                Kind.EXACT, ChannelMatcher.matchChannel(List.of(" CD3 "), "CD3").kind());
    }

    @Test
    void weakestReportsTheWorstTier() {
        var ms = ChannelMatcher.matchAll(CHANNELS, List.of("CD3", "CD20"), false);
        assertEquals(Kind.FUZZY, ChannelMatcher.weakest(ms));
        ms = ChannelMatcher.matchAll(CHANNELS, List.of("CD3", "FOXP3"), false);
        assertEquals(Kind.NONE, ChannelMatcher.weakest(ms));
        assertEquals(Kind.EXACT, ChannelMatcher.weakest(List.of()));
    }

    @Test
    void matchAllSkipsBlankNames() {
        assertEquals(
                1,
                ChannelMatcher.matchAll(CHANNELS, java.util.Arrays.asList("CD3", "", null), true)
                        .size());
    }
}
