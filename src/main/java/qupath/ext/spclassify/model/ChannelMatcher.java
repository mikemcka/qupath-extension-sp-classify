package qupath.ext.spclassify.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves a marker / channel name against an image's channel names, reporting <i>how</i> it
 * matched. Pure and JavaFX-free so it backs both the review-time auto channel switch
 * ({@code ChannelSelector}) and the channel-mapping editor's per-entry status column.
 *
 * <p>Match tiers, strongest first:
 * <ol>
 *   <li>{@link Kind#EXACT} — the name equals a channel name verbatim (after trimming).</li>
 *   <li>{@link Kind#NORMALIZED} — equal after lowercasing and dropping everything that is not
 *       a letter or digit ({@code "CD3_S2 - Cy5"} ≡ {@code "cd3s2cy5"}).</li>
 *   <li>{@link Kind#FUZZY} — one normalized name contains the other ({@code "CD3"} vs
 *       {@code "CD3 (Opal 570)"}). Only used when no channel matches at a stronger tier, and
 *       reported so the editor can flag it: a substring match can pick the wrong channel.</li>
 *   <li>{@link Kind#NONE} — nothing matched.</li>
 * </ol>
 */
public final class ChannelMatcher {

    private ChannelMatcher() {} // utility class

    /** How a name resolved against the channel list. Declared strongest → weakest. */
    public enum Kind {
        EXACT,
        NORMALIZED,
        FUZZY,
        NONE
    }

    /**
     * The result of resolving one name.
     *
     * @param query   the name that was looked up
     * @param kind    the tier it matched at
     * @param indices 0-based indices into the channel list that it resolved to (empty for NONE)
     */
    public record Match(String query, Kind kind, List<Integer> indices) {
        public Match {
            indices = List.copyOf(indices);
        }

        public boolean matched() {
            return kind != Kind.NONE;
        }
    }

    /**
     * Lowercase and drop every non-alphanumeric character, so matching tolerates spacing, dashes,
     * underscores and parentheses that differ between a typed marker name and a channel name.
     */
    public static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    /**
     * Legacy marker matching (the behaviour imported CSV marker names have always had): every
     * channel whose normalized name equals the normalized marker is chosen; only when there is
     * none does the substring fallback run. A verbatim match is reported as {@link Kind#EXACT}
     * but does not exclude other normalized-equal channels.
     */
    public static Match matchMarker(List<String> channelNames, String marker) {
        String m = normalize(marker);
        if (channelNames == null || m.isEmpty()) return new Match(marker, Kind.NONE, List.of());
        String trimmed = marker.strip();

        List<Integer> normalizedEq = new ArrayList<>();
        List<Integer> fuzzy = new ArrayList<>();
        boolean anyVerbatim = false;
        for (int i = 0; i < channelNames.size(); i++) {
            String name = channelNames.get(i);
            String normCh = normalize(name);
            if (normCh.isEmpty()) continue;
            if (normCh.equals(m)) {
                normalizedEq.add(i);
                if (name.strip().equals(trimmed)) anyVerbatim = true;
            } else if (normCh.contains(m) || m.contains(normCh)) {
                fuzzy.add(i);
            }
        }
        if (!normalizedEq.isEmpty()) {
            return new Match(marker, anyVerbatim ? Kind.EXACT : Kind.NORMALIZED, normalizedEq);
        }
        if (!fuzzy.isEmpty()) return new Match(marker, Kind.FUZZY, fuzzy);
        return new Match(marker, Kind.NONE, List.of());
    }

    /**
     * Exact-first matching for channel names chosen in the mapping editor: a verbatim match
     * selects only that channel; otherwise it falls back to {@link #matchMarker} so a mapping
     * made on one image still resolves on another whose channel names differ cosmetically.
     */
    public static Match matchChannel(List<String> channelNames, String channel) {
        if (channelNames == null || channel == null || channel.isBlank()) {
            return new Match(channel, Kind.NONE, List.of());
        }
        String trimmed = channel.strip();
        for (int i = 0; i < channelNames.size(); i++) {
            String name = channelNames.get(i);
            if (name != null && name.strip().equals(trimmed)) {
                return new Match(channel, Kind.EXACT, List.of(i));
            }
        }
        return matchMarker(channelNames, channel);
    }

    /** Resolve each name with {@link #matchChannel} (exactFirst) or {@link #matchMarker}. */
    public static List<Match> matchAll(List<String> channelNames, List<String> names, boolean exactFirst) {
        if (names == null || names.isEmpty()) return Collections.emptyList();
        List<Match> out = new ArrayList<>(names.size());
        for (String n : names) {
            if (n == null || n.isBlank()) continue;
            out.add(exactFirst ? matchChannel(channelNames, n) : matchMarker(channelNames, n));
        }
        return out;
    }

    /** The weakest tier among {@code matches} (NONE if any name failed); EXACT for an empty list. */
    public static Kind weakest(List<Match> matches) {
        Kind worst = Kind.EXACT;
        for (Match m : matches) {
            if (m.kind().ordinal() > worst.ordinal()) worst = m.kind();
        }
        return worst;
    }
}
