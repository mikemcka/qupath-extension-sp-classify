# Marker table format

The **channel mapping** lists which image channels to show for each class. In [Review Mode](review-mode.md), the viewer then shows only those channels for the current cell. For example, for a cell predicted as `CD4T` it shows CD4 and CD3. (The CSV file version is called a marker table in the **Import** menu.)

Build and edit the channel mapping in the **Channel Mapping editor** (below). Use CSV import and export to share a channel mapping between projects or to load an existing marker table.

## Channel Mapping editor (recommended)

**Menu:** *Extensions → SP Classify → Channel Mapping (Review Display)...* — or the **Edit channel mapping…** button in the [Review Mode](review-mode.md) window, below the two auto-channel checkboxes.

Open an image before you open the editor. The editor lists that image's channels; if no image is open, an error asks you to open one. You can keep using the viewer while the editor is open.

- **Left — classes.** The project's classes, classes known to the trained classifier or labels, and any channel-mapping entry that matches no class. An entry that matches no class (an *orphan*) is shown in *italics* and tagged **(not a project class)**. It is kept until you click **Clear**. A CSV entry whose name differs from a project class only in case, spacing or punctuation (e.g. `CD4 T` vs `CD4T`) is merged into that class and saved under the project class's name.
- **Right — channels.** The open image's channels as a checklist with a filter box. Each channel shows its `(C1)`, `(C2)`… index. Tick the channels to show for the selected class. A note above the list describes the selected entry: where it came from, how each name was matched, and any channels missing from this image.

Each class shows a status symbol for the open image:

| Symbol | Meaning |
|---|---|
| ✓ | Every channel matched exactly. |
| ≈ | Matched, ignoring case / spacing / punctuation. |
| ~ | Matched only by **partial** name — may be the wrong channel. Check it. |
| ! | Some names match nothing in this image. |
| ✗ | Nothing matches. |
| – | No channels set — review leaves the display unchanged for that class. |

Entries that still use marker names from an imported CSV are tagged **[CSV]**.

**Per-class buttons**

- **Clear** — removes the class's channel mapping. For an orphan, this deletes the entry. For a rule-format entry, review uses the rule's markers instead.
- **Pin matches** — replaces the entry's CSV marker names with the exact channel names they match in the open image. Names that match no channel are removed, and a list of them is shown.
- **Preview** — shows that class's channels in the viewer now.

**Bottom buttons**

- **Import CSV…** — reads the same formats as *Extensions → SP Classify → Import → Marker Table...*. It replaces what the editor shows and is **not saved until you click Save**. A summary reports *N exact, N approximate, N unmatched, N not project classes*.
- **Export CSV…** — writes the current channel mapping to a CSV (formats [below](#simple-format)).
- **Pin all matches** — runs **Pin matches** on every entry.
- **Save** — saves the channel mapping to the project. If review is running, the current cell's channels are updated straight away.
- **Cancel** — closes the editor. If you have unsaved changes, it asks before discarding them.

**Scan project channels** opens every image in the project to read its channel names. It runs in the background and can be slow on large projects; click **Cancel scan** to stop it. Channels missing from some images are listed with `[n/N images]`. Channels found only in other images are added to the list and marked *not in this image*.

**How many channels?** There is no limit on channels per class. Above 8, the editor shows a warning that the combined image may be hard to read. You can still save.

### Fixing a CSV that picks the wrong channels

1. Open an image and the editor, then click **Import CSV…**.
2. Look for `~`, `!` and `✗` rows — those matched only by partial name, only in part, or not at all.
3. Select each such class and fix the ticks on the right.
4. Click **Pin all matches** to save the correct matches as exact channel names, then click **Save**.

## Where it is stored

The channel mapping is saved per project, in `<project>/celltune/marker-table.json`. It is kept after QuPath restarts. Opening a different project loads that project's channel mapping (a project without one starts empty). To reuse a channel mapping in another project, click **Export CSV…**, then **Import CSV…** in the other project.

Older versions of SP Classify can open this file but ignore the exact channels you ticked.

The two review checkboxes (**Auto-select channels during review** and **Auto-adjust brightness/contrast of shown channels**) keep their setting across review windows and QuPath restarts.

## CSV import and export

**Extensions → SP Classify → Import → Marker Table...** and the editor's **Import CSV…** accept both formats below:

- Fields in quotes may contain commas.
- The byte-order mark that Excel adds is ignored.
- Extra columns not described below are ignored.

## Simple format

A CSV with `Marker1`–`Marker5` columns, plus any further columns whose header starts with `Marker` (`Marker6`, `Marker7`, …), with no upper limit. Trailing columns may be blank. Exports have as many `MarkerN` columns as needed (at least 5). An example file to copy and edit is at [`examples/marker-table-example.csv`](https://github.com/mikemcka/qupath-extension-sp-classify/blob/main/examples/marker-table-example.csv).

```csv
CellType,Marker1,Marker2,Marker3,Marker4,Marker5
CD4T,CD4,CD3,,,
CD8T,CD8,CD3,,,
Treg,CD4,CD25,FOXP3,CD3,
Bcell,CD20,CD19,,,
Macrophage,CD68,CD163,CD11b,,
```

## How names are matched

!!! tip "Matching rules"
    - `CellType` must match your class names. Case, spaces and punctuation are ignored (`CD4 T` = `cd4t` = `CD4-T`).
    - Marker names are matched to channel names the same way. `CD3` matches a channel named `CD3 (Opal 570)`.
    - Names are matched against the image's own channel names, not QuPath's display names with ` (C4)` added. Names typed with the `(C4)` suffix still work.
    - Channels you ticked in the editor are matched by exact name first. If that name is not in the image, the matching above is used. Channels not in the current image are kept for other images.
    - If a class is not in the table, or none of its markers match a channel, the viewer channels are not changed.

## Rule format (gating)

The importer also accepts a **rule format** for composite gating. A file with a `PrimaryMarker` column is read as rule format. See **[Binary + composite workflow](binary-composite.md)** for how gating rules are written and applied.

```csv
CellType,PrimaryMarker,SecondaryMarker,TertiaryMarker
CD8T,CD8&CD3,CD45,CD103|CD45RA
Macrophage,CD68|CD163|CD206,,CD14|CD38|VIM
```

The rule format can also have a `DisplayChannels` column: exact channel names separated by `|`. **Export CSV…** writes this column when exact channels are set. Older versions of SP Classify ignore it.
