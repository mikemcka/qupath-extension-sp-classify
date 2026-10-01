# Marker table format

A **marker table** (also called the **channel mapping**) maps each cell type to the image channels that identify it. It drives
[auto channel-switching during review](review-mode.md) — when you land on a predicted `CD4T`
cell, the viewer can show the `CD4`/`CD3` channels automatically.

The recommended way to build and maintain it is the **Channel Mapping editor**
(see below). CSV import/export remains available for sharing a mapping between projects and for
legacy tables.

## Channel Mapping editor (recommended)

**Menu:** *Extensions ▸ SP Classify ▸ Channel Mapping (Review Display)...* — or the
**Edit channel mapping…** button in the [Review Mode](review-mode.md) window, beneath the two
auto-channel checkboxes.

The editor needs an **open image**, because that image's channels are the choices you pick from
(otherwise an error asks you to open one first). It is **non-modal**, so the viewer stays usable
while it is open.

- **Left — classes.** Your project's classes, plus any class the trained classifier or labels know
  about, plus any marker-table entry that matches no class. Such an entry is shown in *italic* and
  tagged **(not a project class)** — an *orphan*. Orphans are kept unless you **Clear** them. A CSV
  entry whose name differs from a project class only in case, spacing or punctuation (e.g. `CD4 T`
  vs `CD4T`) is merged into that class and saved under the project class's exact name.
- **Right — channels.** The open image's channels as a filterable checklist, each shown with its
  `(C1)`, `(C2)`… index. Tick the channels review should show for the selected class. A note above
  the list explains the entry (it came from a CSV and how each name resolved, a gating rule was kept
  unchanged, the entry was renamed, it is an orphan, some channels are not in this image, etc.).

Each class carries a status glyph against the open image:

| Glyph | Meaning |
|---|---|
| ✓ | Every channel matched exactly. |
| ≈ | Matched, ignoring case / spacing / punctuation. |
| ~ | Matched only by **partial** name — may be the wrong channel. Check it. |
| ! | Some names match nothing in this image. |
| ✗ | Nothing matches. |
| – | No channels set — review leaves the display unchanged for that class. |

Entries still driven by imported CSV marker names are tagged **[CSV]**.

**Per-class buttons**

- **Clear** — removes the class's mapping. For an orphan this deletes the entry; for a rule-format
  entry, review falls back to the rule's markers.
- **Pin matches** — replaces this entry's name-matched CSV markers with the exact channels they
  currently resolve to. Names that match nothing are dropped and listed.
- **Preview** — shows that class's channels in the viewer right now.

**Bottom buttons**

- **Import CSV…** — same formats as *Import ▸ Marker Table*. It replaces what the editor shows and is
  **not saved until you press Save**. A summary reports *N exact, N approximate, N unmatched, N not
  project classes*.
- **Export CSV…** — writes the current mapping out (formats [below](#simple-format)).
- **Pin all matches** — **Pin matches** for every entry at once.
- **Save** — writes the mapping to the project. If review is running, the current cell's channels
  are re-applied immediately.
- **Cancel** — closes the editor; asks before discarding unsaved changes.

**Scan project channels** reads every project image's channel names in the background (cancellable).
Channels missing from some images are listed with `[n/N images]`; channels that exist only in other
images are added to the list marked *not in this image*. Opening each image's server can take a
while on big projects.

**How many channels?** There is **no limit** per class (the old 5-marker cap is gone). Above 8 the
editor shows a soft warning that the composite may be hard to read; it never blocks you.

### Fixing a CSV that picks the wrong channels

1. Open an image and the editor, then **Import CSV…**.
2. Look for `~`, `!` and `✗` rows — those matched only by partial name, only in part, or not at all.
3. Select each such class and fix the ticks on the right.
4. Click **Pin all matches** to lock the good matches to exact channels, then **Save**.

## Where it is stored

The mapping is **per project**, saved to `<project>/celltune/marker-table.json`. It survives QuPath
restarts, and each project keeps its own mapping: switching projects loads that project's mapping
(a project without one starts empty). To reuse a mapping in another project, **Export CSV…** and
**Import CSV…** it there.

The file is now schema **version 2**, which adds an optional per-entry `channels` list holding the
exact channel names you ticked. Older versions of the extension can still read it — they ignore the
exact channels and use the markers.

The two review checkboxes (**Auto-select channels during review** and **Auto-adjust brightness/contrast
of shown channels**) remember their state across review windows and QuPath restarts.

## CSV import and export

**Extensions ▸ SP Classify ▸ Import ▸ Marker Table...** is unchanged and both formats below still
work (the editor's **Import CSV…** reads the same files). Reading is more forgiving than before:

- Quoted fields may contain commas (RFC 4180).
- The UTF-8 byte-order mark that Excel adds is ignored (it previously broke rule-format CSVs).
- Extra columns that aren't described below are ignored.

## Simple format

A CSV with `Marker1`–`Marker5` columns, plus any further columns whose header starts with
`Marker` (`Marker6`, `Marker7`, …) — there is no upper limit. Trailing columns may be left blank.
Exports widen to as many `MarkerN` columns as needed (at least 5). A ready-to-edit example
lives at
[`examples/marker-table-example.csv`](https://github.com/mikemcka/qupath-extension-sp-classify/blob/main/examples/marker-table-example.csv).

```csv
CellType,Marker1,Marker2,Marker3,Marker4,Marker5
CD4T,CD4,CD3,,,
CD8T,CD8,CD3,,,
Treg,CD4,CD25,FOXP3,CD3,
Bcell,CD20,CD19,,,
Macrophage,CD68,CD163,CD11b,,
```

## How names are matched

!!! tip "Matching is tolerant — but the `CellType` column should track your class names"
    The `CellType` column should match the class names you assign to labelled cells. Matching is
    **case-, spacing-, and punctuation-insensitive**, so `CD4 T`, `cd4t`, and `CD4-T` are treated
    as the same type. `Marker` names are matched to image channels the same way, so a channel
    named `CD3 (Opal 570)` still matches the marker `CD3`.

    If a predicted type isn't found in the table, or none of its markers match any channel, the
    viewer's channels are **left unchanged** (nothing is hidden).

    Matching uses the image's **own channel names**. Earlier versions matched against QuPath's
    display names, which append ` (C<n>)` (e.g. `CD3 (C4)`), so exact matches rarely fired and `CD3`
    could also light up `CD31`. If a table ever showed the wrong channels, re-check it in the
    editor. Names typed as display names (with the `(C4)` suffix) still work.

    Exact channels chosen in the editor are matched **verbatim first**; if an image lacks that exact
    name, the usual tolerant matching is used. So a mapping made on one image works on others whose
    channel names are the same, or cosmetically different. Channels stored in the mapping but absent
    from the current image are kept, not discarded.

## Rule format (gating)

The importer also accepts a **rule format** for composite gating, auto-detected from a
`PrimaryMarker` column. See **[Binary + composite workflow](binary-composite.md)** for how gating
rules are written and applied.

```csv
CellType,PrimaryMarker,SecondaryMarker,TertiaryMarker
CD8T,CD8&CD3,CD45,CD103|CD45RA
Macrophage,CD68|CD163|CD206,,CD14|CD38|VIM
```

The rule format may also carry an optional `DisplayChannels` column — **pipe-separated exact
channel names** — which *Export* writes when exact channels exist. Older versions of the extension
ignore it.
