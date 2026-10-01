# User Guide

A cell classifier for QuPath 0.7 that learns from cells you label. It trains two models on the same labels (XGBoost and LightGBM by default) and lists the cells where the two models predict different classes, so you can check and correct those cells first. It runs inside QuPath and does not need Python.

On large projects or images, windows and tasks can take several seconds to open or start. Wait before clicking again.

If a window opens with buttons cut off, drag its corner to make it larger. The cursor changes to a resize arrow over the corner.

> **Conventions in this guide**
> - **Bold** = exact UI label.
> - `Monospace` = file path or code.
> - "Multi-class mode" = the standard sidebar (any number of cell-type classes).
> - "Binary mode" = a per-marker positive/negative classifier (CD4+/CD4−, etc.).

---

## Table of Contents

1. [Install & launch](#1-install--launch)
2. [Quick start — multi-class workflow](#2-quick-start--multi-class-workflow)
3. [Quick start — binary + composite workflow](#3-quick-start--binary--composite-workflow)
4. [Setup steps (shared by both workflows)](#4-setup-steps-shared-by-both-workflows)
   - [4.1 Select features](#41-select-features)
   - [4.2 Clustering normalisation](#42-clustering-normalisation)
   - [4.3 Create classes & Class Control](#43-create-classes--class-control)
   - [4.4 Channel mapping for review (marker table)](#44-channel-mapping-for-review-marker-table)
5. [Multi-class workflow in detail](#5-multi-class-workflow-in-detail)
6. [Binary + composite workflow in detail](#6-binary--composite-workflow-in-detail)
7. [After training — Review mode](#7-after-training--review-mode)
8. [Project Prediction Summary](#8-project-prediction-summary)
9. [Intensity heatmaps](#9-intensity-heatmaps)
10. [Distance measurements (spatial analysis)](#10-distance-measurements-spatial-analysis)
11. [Cell scatter plot — clustering & gating](#11-cell-scatter-plot--clustering--gating)
    - [11.1 Controls](#111-controls)
    - [11.2 Selecting cells](#112-selecting-cells)
    - [11.3 Apply Clusters — assign classes to clusters](#113-apply-clusters--assign-clusters--assign-classes-to-clusters)
    - [11.4 Cluster-within-clusters (hierarchical gating)](#114-cluster-within-clusters-hierarchical-gating)
    - [11.5 Project-wide clustering across images](#115-project-wide-clustering-across-images)
    - [11.6 Clustering method: k-means vs Leiden](#116-clustering-method-k-means-vs-leiden)
12. [Exporting results](#12-exporting-results)
    - [12.1 Cell table export](#121-cell-table-export)
    - [12.2 Ground truth export & import](#122-ground-truth-export--import)
13. [Utility scripts](#13-utility-scripts)
    - [13.1 Filter Cells by Size & Circularity](#131-filter-cells-by-size--circularity)
    - [13.2 Resolve Hierarchy](#132-resolve-hierarchy)
    - [13.3 Delete Measurements by Keyword](#133-delete-measurements-by-keyword)
    - [13.4 Import GeoJSON Objects](#134-import-geojson-objects)
    - [13.5 Export Annotation Regions](#135-export-annotation-regions)
    - [13.6 Reset Project State](#136-reset-project-state)
14. [Reference: every setting in the sidebar](#14-reference-every-setting-in-the-sidebar)
    - [14.1 Reference: preferences](#141-reference-preferences)
15. [Reference: every SP Classify menu item](#15-reference-every-sp-classify-menu-item)
16. [Project directory layout](#16-project-directory-layout)
17. [Image pixel prescreen (whole-image QC)](#17-image-pixel-prescreen-whole-image-qc-no-cells-needed)
18. [Cellular neighborhoods (spatial micro-environments)](#18-cellular-neighborhoods-spatial-micro-environments)
    - [18.1 When to use it](#181-when-to-use-it)
    - [18.2 How the clusters are computed](#182-how-the-clusters-are-computed)
    - [18.3 Scope: current image vs whole project](#183-scope-current-image-vs-whole-project)
    - [18.4 Running it — step by step](#184-running-it--step-by-step)
    - [18.5 The enrichment heatmap — reading, naming, merging](#185-the-enrichment-heatmap--reading-naming-merging)
    - [18.6 Parallel workers (project scope) — performance](#186-parallel-workers-project-scope--performance)
    - [18.7 Viewer overlays](#187-viewer-overlays)
    - [18.8 Tips & cautions](#188-tips--cautions)
19. [Batch normalisation (UniFORM)](#19-batch-normalisation-uniform)
    - [19.1 When to use it](#191-when-to-use-it)
    - [19.2 Fitting — step by step](#192-fitting--step-by-step)
    - [19.3 QC — did it work?](#193-qc--did-it-work)
    - [19.4 How it's applied](#194-how-its-applied)
    - [19.5 Tips & cautions](#195-tips--cautions)
20. [Tips, tricks and known limitations](#20-tips-tricks-and-known-limitations)

---

## 1. Install & launch

1. **Download** `qupath-extension-sp-classify-0.3.1-all.jar` from the [Releases page](https://github.com/mikemcka/qupath-extension-sp-classify/releases), or build it from source — see [CLAUDE.md](CLAUDE.md#build--test).
2. Delete any older `qupath-extension-sp-classify-*-all.jar` from QuPath's extensions folder. If an old copy stays there, QuPath may load it instead of the new one. Then copy the new JAR into the folder, or drag it onto the QuPath window:
   - Windows: `C:\Users\<you>\QuPath\v0.7\extensions\`
   - Linux: `~/.local/share/QuPath/v0.7/extensions/`
   - macOS: `~/Library/Application Support/QuPath/v0.7/extensions/`
3. Restart QuPath. **SP Classify** is added as a tab in the analysis pane (the panel with the Project, Image and Annotations tabs). If the tab is not visible, hold the mouse over the row of tab names and scroll the mouse wheel until **SP Classify** appears.
4. Other commands are in the **Extensions → SP Classify** menu.

To turn the extension off, go to **Edit → Preferences → SP Classify** and untick **Enable SP Classify extension**.

![Docked side panel](doc_images/docked_side_panel.png)

![Dropdown](doc_images/dropdown_menu.png)

---

## 2. Quick start — multi-class workflow

Build one classifier that distinguishes any number of cell types (e.g. T-cell / B-cell / Macrophage / Tumour / Stroma).

```
Select Features  →  Clustering Normalisation  →  Create classes (Class Control)
       ↓
Channel Mapping (optional, for auto channel switching)
       ↓
Manual Label Mode  → label at least 20–30 cells per class
                     (training needs at least 10 labelled cells in total)
       ↓
Apply to which images... → choose images to predict on
       ↓
Set training options: Pool labels from all images, Enable data balancing,
Early stopping, Images at once (images predicted in parallel; default 1, max 8)
       ↓
Train  →  inspect Training Metrics + Confusion Matrix
       ↓
Enter Review Mode  →  correct cells the models disagree on
       ↓
(repeat: more labels → Train → Review)
       ↓
Project Prediction Summary  →  flag outlier slides → re-label as needed
       ↓
Export Cell Table  or  Export Ground Truth
```

Each step is described in §[4](#4-setup-steps-shared-by-both-workflows), §[5](#5-multi-class-workflow-in-detail) and §[7](#7-after-training--review-mode).

---

## 3. Quick start — binary + composite workflow

Build one **positive/negative classifier per marker** (CD3, CD4, CD8, CD20…), then combine them into composite cell types (`CD3+:CD4+:CD8-`, etc.).

```
Select Features  →  Clustering Normalisation
       ↓
Extensions → SP Classify → Binary Classifiers... → Create... (name it CD3)
→ Open (enters Binary Mode)
       ↓
Manual Label Mode → label CD3-positive vs CD3-negative cells
       ↓
Apply to which images... → Train → Review Mode (correct disagreements)
       ↓
Exit Binary Mode → repeat for CD4, CD8, CD20, etc.
       ↓
Composite Classification... → tick markers, tick images
       ↓
(optional) tick "Prepend current primary classification (colour follows
primary)" so each cell keeps its multi-class class name and colour in
front of the marker labels
       ↓
Apply → composite labels appear in viewer (Tumour:CD3+:CD8-, …)
       ↓
Export Cell Table
```

Each step is described in §[6](#6-binary--composite-workflow-in-detail).

---

## 4. Setup steps (shared by both workflows)

### 4.1 Select features

**Menu:** *Extensions → SP Classify → Select Features...*

QuPath cell-detection panels (COMET, MIBI, IMC, CODEX) often produce 1000–2000 measurement columns per cell. Pick the subset to use for training. Unticked features are not used.

- **Filter:** box — case-insensitive text search. Groups that contain a match open automatically.
- **Grouped checkbox tree** — features are listed in collapsible groups (below). Tick a group's box to tick or untick every feature in it.
- **Select All** / **Clear All** — tick or untick every feature currently shown by the filter.
- **Expand All** / **Collapse All** — open or close every group.
- Tick or untick a row to change one feature.
- The counter at the bottom shows `X / Y selected`.

There is one group per **marker** (the text before the first `: `, e.g. `DAPI_AF`), followed by these groups:

- **Morphology / Shape** — compartment-only measurements (`Cell: Area µm^2`, `Nucleus: Circularity`, …).
- **Neighbors** — neighbour-aggregate features (`Neighbors: Mean: …`). Labels keep the `Neighbors:` prefix.
- **Embeddings** — dimensionality-reduction and embedding columns: UMAP, PCA, t-SNE, and `*_emb_*` names (e.g. `kronos_emb_0`).
- **Other / Uncategorized** — features that fit none of the groups above.

![Feature selection](doc_images/feature_selection.png)

**Do you need to remove features by hand?** Usually not. Extra or near-duplicate features rarely lower accuracy with the default models, but they make training slower and split a marker's importance across several columns in the feature-importance plot. Leave **Auto-prune features** ticked to remove them automatically.

**Auto-prune features (drop near-constant & redundant)** is ticked by default (§[14](#14-reference-every-setting-in-the-sidebar)). At the start of each training run it removes features from that run's feature list. It does not delete or change measurements. The decision uses raw values from your labelled cells on the open image, plus labelled cells from other images when **Pool labels from all images** is ticked. Rows imported from CSV are trained on but not used for the decision. Pruning is skipped if there are fewer than 10 labelled cells. It works in four steps:

1. **Constant features** — removes a feature that is non-zero in fewer than 5 cells or has zero variance.
2. **Duplicates within a marker** — within each marker group, keeps the feature with the highest variance and removes any feature whose absolute Pearson correlation with a kept feature is above 0.95. For example, `CD3: Cell: Mean`, `CD3: Cell: Median` and `CD3: Cell: Max` become one column.
3. **Duplicates across markers** — not removed. Features from two different markers are never merged, even when they are correlated.
4. **Minimum kept per marker** — the 5 highest-variance features in each group are always kept, so every marker keeps at least 5 features (or all of them if it has 5 or fewer).

> **Marker groups for pruning:** a feature's group is the text before the first `: ` (`CD3: Cell: Mean` → CD3). If there is no `: `, the group is the text before the first underscore or space (`kronos_emb_0` → kronos, `Distance to tumor` → distance). Case is ignored. These groups are not the same as the Morphology / Neighbors / Embeddings groups in the feature picker.

Your selection is saved in `<project>/celltune/classifier-state.json` and is kept between QuPath sessions.

### 4.2 Clustering normalisation

**Menu:** *Extensions → SP Classify → Clustering Normalisation*

These transforms are used only by clustering, the scatter plot and gating (§[11](#11-cell-scatter-plot--clustering--gating)). The classifier always uses raw values. The window has a **Filter:** search box, a prefix dropdown with **Select Prefix** / **Clear Prefix**, **Select All** / **Clear All**, and:

- **Transform:** dropdown:
  - **arcsinh** — `arcsinh(x / cofactor)`. Recommended. Set **Cofactor:** (default 1.0) for your data, or click **Suggest…** to open a tool that estimates a cofactor from the background level of features you choose.
    - Raw fluorescence (COMET, CODEX, IF; values in the hundreds to thousands): 25–50.
    - MIBI: 0.05 (Hartmann et al. 2021; see [References](README.md#references)).
    - Choose a value near the boundary between background and positive signal. If almost all raw values are below the cofactor, the transform changes very little. If almost all are far above it, low-intensity differences are lost.
  - **sqrt** — `sqrt(max(0, x))`. No cofactor.

![Clustering normalisation](doc_images/normalise_features.png)

The one transform and cofactor apply to every ticked feature. Unticked features stay raw. Do not tick morphology features (e.g. Cell Area) or features that are already normalised (e.g. foundation-model embeddings).

**Why use it.** Clustering compares cells by distance across all markers. Without a transform, a few very bright markers decide most of the result. arcsinh reduces the effect of bright outliers and keeps differences between dim cells.

**What it does not do.** It does not change the classifier, auto-prune, feature importance or ground-truth export, which all use raw values. It does not correct differences in staining or exposure between slides, because the same transform is applied to every image. For that, use batch normalisation (§[19](#19-batch-normalisation-uniform)) and label cells on several different slides.

**What clustering does automatically.** The transform in this window is optional. Every clustering run also:

1. z-scores each marker (subtracts the mean and divides by the standard deviation), and
2. applies PCA when more than 50 markers are selected and **Reduce dims (PCA)** in the scatter plot is ticked (default: ticked).

```
(optional arcsinh / sqrt)  →  z-score each marker  →  (PCA if > 50 markers)  →  k-means / Leiden
```

If you set no transform here, clustering runs on z-scored raw values. arcsinh adds one thing z-scoring cannot: it reduces extreme high values within a marker.

### 4.3 Create classes & Class Control

**Menu:** *Extensions → SP Classify → Class Control...*

A dialog with 4 tabs for managing QuPath's class list **and** the labels saved under `<project>/celltune/image-labels/`.

#### Add tab
Type a class name and click **Add Class**. This adds the class to QuPath's class list only. Label files are not changed.

#### Delete tab
- Pick a class from the list.
- Tick **Also remove labels with this class from all image-label files** to remove the class's labels from every saved label file. Leave it unticked to remove the class from the class list only. The saved labels are kept on disk and are still used for training.
- **Delete Selected Class** (red) — asks for confirmation.

#### Merge tab
- Select one or more source classes (Ctrl/Cmd+click), then type a target name or pick one from **Existing:**.
- **Merge Selected → Target** renames every matching label in all images. The original name is kept in the saved label: `test1` merged into `myType` is saved as `test1-mergedInto(myType)`. Training uses the target class (`myType`). Undo it from the **Undo Merge** tab.

#### Undo Merge tab
- Pick a class that was a merge target. The list shows classes found in `-mergedInto(...)` labels.
- **Undo Merge for Selected Class** — restores each label to its original name and adds the original class back to the class list. The target class is **not** deleted. Remove it from the **Delete** tab if you no longer need it.

### 4.4 Channel mapping for review (marker table)

Optional. The **channel mapping** lists which image channels to show for each class. In [Review Mode](#7-after-training--review-mode), the viewer then shows only those channels for the current cell. For example, for a cell predicted as `CD4T` it shows CD4 and CD3. (The CSV file version is called a marker table in the **Import** menu.)

Build and edit the channel mapping in the **Channel Mapping editor**. Use CSV import and export to share a channel mapping between projects or to load an existing marker table.

**Menu:** *Extensions → SP Classify → Channel Mapping (Review Display)...* — or the **Edit channel mapping…** button in the Review Mode window, below the two auto-channel checkboxes.

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

#### Fixing a CSV that picks the wrong channels

1. Open an image and the editor, then click **Import CSV…**.
2. Look for `~`, `!` and `✗` rows — those matched only by partial name, only in part, or not at all.
3. Select each such class and fix the ticks on the right.
4. Click **Pin all matches** to save the correct matches as exact channel names, then click **Save**.

#### Where it is stored

The channel mapping is saved per project, in `<project>/celltune/marker-table.json`. It is kept after QuPath restarts. Opening a different project loads that project's channel mapping (a project without one starts empty). To reuse a channel mapping in another project, click **Export CSV…**, then **Import CSV…** in the other project.

Older versions of SP Classify can open this file but ignore the exact channels you ticked.

The two review checkboxes (**Auto-select channels during review** and **Auto-adjust brightness/contrast of shown channels**) keep their setting across review windows and QuPath restarts.

#### CSV import and export

**Extensions → SP Classify → Import → Marker Table...** and the editor's **Import CSV…** accept both formats below:

- Fields in quotes may contain commas.
- The byte-order mark that Excel adds is ignored.
- Extra columns not described below are ignored.

##### Simple format

A CSV with `Marker1`–`Marker5` columns, plus any further columns whose header starts with `Marker` (`Marker6`, `Marker7`, …), with no upper limit. Trailing columns may be blank. Exports have as many `MarkerN` columns as needed (at least 5). An example file to copy and edit is at [`examples/marker-table-example.csv`](https://github.com/mikemcka/qupath-extension-sp-classify/blob/main/examples/marker-table-example.csv).

```csv
CellType,Marker1,Marker2,Marker3,Marker4,Marker5
CD4T,CD4,CD3,,,
CD8T,CD8,CD3,,,
Treg,CD4,CD25,FOXP3,CD3,
Bcell,CD20,CD19,,,
Macrophage,CD68,CD163,CD11b,,
```

##### How names are matched

> - `CellType` must match your class names. Case, spaces and punctuation are ignored (`CD4 T` = `cd4t` = `CD4-T`).
> - Marker names are matched to channel names the same way. `CD3` matches a channel named `CD3 (Opal 570)`.
> - Names are matched against the image's own channel names, not QuPath's display names with ` (C4)` added. Names typed with the `(C4)` suffix still work.
> - Channels you ticked in the editor are matched by exact name first. If that name is not in the image, the matching above is used. Channels not in the current image are kept for other images.
> - If a class is not in the table, or none of its markers match a channel, the viewer channels are not changed.

##### Rule format (gating)

The importer also accepts a **rule format** for composite gating. A file with a `PrimaryMarker` column is read as rule format. See **[Binary + composite workflow](#6-binary--composite-workflow-in-detail)** for how gating rules are written and applied.

```csv
CellType,PrimaryMarker,SecondaryMarker,TertiaryMarker
CD8T,CD8&CD3,CD45,CD103|CD45RA
Macrophage,CD68|CD163|CD206,,CD14|CD38|VIM
```

The rule format can also have a `DisplayChannels` column: exact channel names separated by `|`. **Export CSV…** writes this column when exact channels are set. Older versions of SP Classify ignore it.

---

## 5. Multi-class workflow in detail

### 5.1 Initial manual labelling

Click **Manual Label Mode** in the sidebar. A floating toolbar opens:

![Manual label mode](doc_images/manual_label_mode.png)

- Click a cell in the viewer. The toolbar shows its ID and current class. The status dot is lime if the cell has a class and white if it has none.
- The selected cell has a magenta ring.
- Up to 12 class buttons are shown in the toolbar. Other classes are under **All Classes ▼**.
- **Auto-advance to next detection**: when ticked, the next cell is selected after you assign a label.

**How many cells to label:** label at least 20–30 cells per class before the first training run. Training does not start with fewer than 10 labelled cells in total. Add more labels after each review cycle.

> After the first training run, two more buttons appear: **Model 1: CD8 (87%)** (blue) and **Model 2: CD4 (65%)** (pink). Each shows that model's predicted class and confidence for the selected cell. Click one to accept that prediction.

### 5.2 Choose images to apply the classifier to

Click **Apply to which images...** in the sidebar.

![Images to apply](doc_images/select_images.png)

- The left list holds the images the classifier will be applied to. The right list holds excluded images.
- Use the search boxes and the arrow buttons (`>`, `>>`, `<`, `<<`) to move images between the lists.
- The open image is always included and cannot be moved.
- Click **OK**. The button then shows the number of images, e.g. `Apply to which images... (12)`.

Choose fewer images to make the apply step faster.

### 5.3 CPU threads and Images at once

![Compute settings: Rounds, Max depth, CPU threads, Images at once](doc_images/classifier_compute_settings.png)

These two settings control speed. If their labels are cut off, widen the panel or hold the mouse over a control to see its tooltip.

**CPU threads** sets how many processor threads training uses. The default is **0**, which uses all processors. Lower it to keep QuPath responsive during training, or when you share a compute node. The value is remembered between sessions. Changing it can change results very slightly, so use the same value when you compare two training runs.

**Images at once** sets how many images are classified at the same time after training. The default is 1 and the range is 1–8. It has no effect on training. Each image is loaded fully into memory, so a higher value uses more memory. Suggested values: 1 on a 16 GB machine, 2–3 on 32 GB. If QuPath runs out of memory, set it back to 1.

### 5.4 Pick the right settings

Most users can leave these at their defaults.

| Setting | Default | Turn on when… | Turn off when… |
|---|---|---|---|
| **Pool labels from all images** | ✅ | recommended (always on in binary mode) | you want a model trained on the open image's labels only |
| **Enable data balancing** + `SMOTE + Tomek` | ✅ | one class has many fewer labels than others | classes are already balanced, or you want to train on the labels as they are |
| **Auto-tune hyperparameters** | ❌ | you can leave training running for several hours (see **Auto-tune** below) | you are still adding labels |
| **Early stopping** | ✅ | recommended | you need a fixed number of rounds, e.g. to reproduce a published setting |
| **Train/val metrics** | ✅ | you want the Training Metrics report | you are still adding labels (see below) |
| **Show top 10 feature importance after training** | ✅ | recommended | you do not want the chart to open after training |
| **Auto-prune features (drop near-constant & redundant)** | ✅ | recommended (faster training; no measurements are deleted) | you need every selected feature used, e.g. for a benchmark |
| **Restrict to features shared with imported data** | ❌ | you imported labels from a project with a different panel | you train on this project's labels only |
| **Sample current image only** | ❌ | you want to review cells from the open image only (§5.7) | you want to review cells from every image (default) |

**Resampling strategies** (the **Strategy:** dropdown, shown when **Enable data balancing** is ticked):

Leave this at the default (`SMOTE + Tomek`) unless you have a specific reason to change it.

| Strategy | Effect |
|---|---|
| `None` | No resampling |
| `SMOTE` | Adds synthetic cells to small classes, made from each cell's 5 nearest cells of the same class |
| `ADASYN` | Like SMOTE, but adds more synthetic cells where a small class is hard to separate from other classes |
| `Tomek links` | Finds pairs of cells of different classes that are each other's nearest neighbour, and removes the cell from the larger class |
| `SMOTE + Tomek` (default) | SMOTE, then Tomek links |
| `ADASYN + Tomek` | ADASYN, then Tomek links |

Use `SMOTE` alone if Tomek links removes too many labelled cells (the training log shows how many it removed). Use `ADASYN` if a small class is often confused with a larger one.

**Train/val metrics** produces the **Training Metrics** report (§5.6). It adds about a third to training time and does not change the trained classifier. Untick it while you are still adding labels, and tick it again for your final run.

**Auto-tune** tests different model settings and keeps the best settings for each model. It trains 2 × **Trials** × **CV folds** models. The defaults are 20 trials and 5 folds, which is 200 models. Trials can be set from 5 to 100 and CV folds from 2 to 10; these controls are shown when **Auto-tune hyperparameters** is ticked. With many features and classes this can take several hours. The training log shows the number of models before it starts.

**Model 1 and Model 2.** The default is XGBoost (Model 1) and LightGBM (Model 2). Random Forest is also available. Use two different model types: review mode depends on the two models disagreeing. Training runs on the CPU only.

**Rounds / Max depth.** The defaults are 500 rounds (range 50–1000) and depth 6 (range 2–15).

- With **Early stopping** ticked (the default), Rounds is a maximum. Each model stops adding rounds when it stops improving.
- With **Early stopping** unticked, every round is trained. Lower Rounds to reduce training time.

The training log shows the rounds each model used, e.g. `XGBoost early stopping: best round 127/500`. If the number is close to the maximum, raise **Rounds**.

### 5.5 Train

Click **Train**. A progress window shows the current step. Before training starts:

- a backup of your labels is saved to `<project>/celltune/labels_backup_*.json`.
- SP Classify estimates how much memory training needs. If the estimate is more than 80% of the memory available to QuPath, a warning asks **Proceed anyway?** The estimate is approximate. To give QuPath more memory, set **Edit → Preferences → Maximum memory** and restart QuPath. To need less memory, select fewer features or set **Strategy:** to `None`.

When training finishes, the sidebar status line shows e.g. `Training complete — 523 cells classified, 47 disagreements.`

#### The training log file

Each training run saves its log to `<project>/celltune/logs/`. The 20 most recent logs are kept. The file is kept after the progress window closes and after a crash. If no project is open, no log file is saved.

The log starts with the cell and label counts, every setting used and the available memory. It ends with the time taken by each step, slowest first:

```
── Where the time went ──────────────────────────────────
  fit XGBoost                326.61s   45.7%
  early stop: XGBoost        293.93s   41.1%
  apply to other images      118.40s    9.2%
  predict all cells           39.88s    5.6%
```

If training is slow, this table shows which step took longest. If a run fails, the last line names the step that failed.

### 5.6 Inspecting the result

Three buttons become available after training: **Agreement Confusion Matrix**, **Training Metrics** and **Feature Importance...**.

#### Confusion Matrix (button)

**Agreement Confusion Matrix** compares the two models' predictions for every cell. Rows are Model 1's predictions and columns are Model 2's. (The axis titles read `Model 1 (XGBoost)` and `Model 2 (LightGBM)` whichever model types you chose.)

- **Diagonal (blue):** cells where both models chose the same class.
- **Off the diagonal (orange/red):** cells where the models chose different classes. Review mode samples from these cells.
- **Right column:** for each Model 1 class, the percentage of its cells that Model 2 also assigned to that class.
- **Bottom row:** for each Model 2 class, the percentage of its cells that Model 1 also assigned to that class.
- **Dice column:** agreement score per class, from 0 (no agreement) to 1 (full agreement).
- **Summary line:** `Total: X cells | Agreement: Y (Z%) | Disagreement: A (B%) | Macro Dice: D`. Macro Dice is the mean Dice over all classes.

If most cells are on the diagonal, the two models mostly agree. A large value off the diagonal shows two classes the models often confuse (e.g. CD4 and CD8). Label more cells of those two classes in the next round.

![Inter-model agreement confusion matrix](doc_images/agreement_confusion_matrix.png)

#### Training Metrics (button)

**Training Metrics** shows precision, recall, F1 and support (number of cells) for each class and each model. These are calculated on 20% of the labelled cells of each class, held back from training:

```
class            precision   recall      f1   support
─────────────────────────────────────────────────────
CD4                  0.925    0.887    0.906       145
CD8                  0.891    0.923    0.907       198
…
─────────────────────────────────────────────────────
accuracy                              0.905       500
macro F1                              0.894       500
weighted F1                           0.903       500
```

Tick **Show 80% training-set rows (for over-fit diagnosis)** to also show the scores on the training cells.

![Training metrics](doc_images/training_metrics.png)

**Validation Confusion Matrix (XGBoost)…** shows the true class (rows) against Model 1's predicted class (columns) for the same 20% of cells. It has two heatmaps: cell counts, and counts as a percentage of each row. The diagonal of the percentage heatmap is each class's recall.

![Validation confusion matrix](doc_images/validation_confusion_matrix.png)

**Exports:**
- **Download CSV…** (Training Metrics window): long format `split,model,class,precision,recall,f1,support`. Summary rows have the class `__accuracy__`, `__macro_f1__` or `__weighted_f1__`, so you can filter them out in pandas or R.
- **Download PNG…** (Validation Confusion Matrix window): both heatmaps side by side. This window also has **Download CSV…**.

> **A high F1 score does not mean the classifier works on new images.** The validation cells come from the same images as the training cells, so F1 overestimates performance on other images. To check, open a different image, apply the classifier, look at the results, and check the Project Prediction Summary (§[8](#8-project-prediction-summary)). If predictions on an image look wrong, label some of its cells and retrain.

#### Feature Importance (button)

**Feature Importance...** shows the 10 features that most affect each class's prediction (mean |SHAP| value), as horizontal bars. Use the **Class:** dropdown to switch class. Values come from the XGBoost model and from any Random Forest model. LightGBM is not included, so with the default pair (XGBoost + LightGBM) the chart shows XGBoost only.

Use it to find features to remove:

- If one feature (e.g. `Cell: DAPI Mean`) ranks highest for every class, de-select it in Select Features (§[4.1](#41-select-features)) and retrain.
- If a non-biological column, such as a cell ID or a centroid coordinate, ranks near the top, it was included in training by mistake. De-select it in Select Features and retrain.

![Feature importance showing a leaked cell-index feature](doc_images/index_feature_leakage.png)

In this example `kronos_cell_id` (a cell index) ranks highest. De-select it in Select Features.

### 5.7 Optional — restrict sampling to specific annotations

Two controls above **Enter Review Mode** limit which cells review mode samples:

- **Sample current image only** (checkbox): sample from the open image only.
- **Specify annotations** (text field): enter words separated by commas, e.g. `Tumour, Margin`. Only cells whose centre is inside an annotation whose name contains one of the words are sampled. Case is ignored.

![Specify annotations before entering review mode](doc_images/review_mode_specifiy_annotations.png)

Leave the box unticked and the field empty to sample from every cell in every project image (the default).

---

## 6. Binary + composite workflow in detail

Use this to train one classifier per marker (e.g. one for CD3 positive/negative, another for CD8 positive/negative) and then combine their results into composite classes. Use it for panels with few markers, or for markers that describe cell state, such as Ki67.

### 6.1 Create a binary classifier

**Menu:** *Extensions → SP Classify → Binary Classifiers...*

1. Click **Create...** and enter a marker name (e.g. `CD3`). Characters other than letters, digits, `.`, `_` and `-` are replaced with `_`. The name cannot start with `.` or `-`.
2. Select the marker in the list and click **Open**. The dialog closes and the sidebar switches to **Binary Mode**, with the banner `Active binary mode: CD3` in blue.

In binary mode:
- The Manual Label Mode class buttons are limited to `CD3_pos` and `CD3_neg`.
- **Pool labels from all images** is ticked and cannot be changed.
- Settings, sampling, review and metrics work as in multi-class mode.

Repeat train → review → retrain until the Training Metrics F1 stops improving and the predictions look correct on images you did not label. Then click **Exit Binary Mode** to return to multi-class mode.

Repeat for each marker you want in the composite.

### 6.2 Composite classification

**Menu:** *Extensions → SP Classify → Composite Classification...*

- **Markers**: one checkbox per trained binary classifier, all ticked by default. Markers you have not trained are not listed. **All** / **None** tick or untick every marker.
- **Images**: one checkbox per project image, all ticked by default. Buttons: **All** / **None** / **Current only**.
- **Prepend current primary classification (colour follows primary)**: see below.
- **Apply**: runs the classifiers.

**What Apply does:**
- The open image updates in the viewer immediately. Other selected images are opened, classified and saved. Progress is shown in the dialog's log.
- Each cell gets a class made of the marker names in text sort order, each followed by `+` or `-`, e.g. `CD3+:CD45+:CD8-`. In text sort order `CD45` comes before `CD8`.
- A marker is `+` when its binary classifier gives a positive probability of 0.5 or higher, and `-` otherwise.

**Prepend current primary classification** (off by default):
- Unticked: the class name has the marker results only (`CD3+:CD8-`). QuPath assigns the colour.
- Ticked: each cell's existing class is added to the front (`Tumour:CD3+:CD8-`), and the cell keeps the colour of its existing class. Cells with no class get the marker-only name.

> Tick **Prepend current primary classification** after running a multi-class classifier, so each cell keeps its cell type and gains the marker results.

---

## 7. After training — Review mode

**Button:** **Enter Review Mode** in the sidebar.

Review mode shows you, one at a time, cells where Model 1 and Model 2 predict different classes (disagreement cells), so you can label them.

Click **Enter Review Mode**. A **Sampling Settings** dialog asks how many disagreement cells to review (default 200; the number available is shown). Cells you have already reviewed are not sampled again.

Cells are picked in five tiers, in order. A cell picked in one tier is not picked again in a later tier.

| Tier | Picks | Budget per 256 cells |
|---|---|---|
| **0 — FOV balance** | cells from the images with the highest disagreement rate | 84 in total, up to 14 per image |
| **1 — Cell-type disagreement** | cells from the classes with the highest disagreement rate | 112 in total, up to 16 per class |
| **2 — Rare cell types** | cells from the classes with the fewest predicted cells | 60 in total, up to 10 per class |
| **3 — Preferred confusions** | cells from chosen class pairs (e.g. `CD4:CD8`) | 40 in total, up to 8 per pair |
| **4 — Random fill** | random disagreement cells, to fill the rest of the batch | the remainder |

> Budgets scale with the number you enter: each is multiplied by (your number ÷ 256) and rounded, with a minimum of 1. There is no control for choosing class pairs, so tier 3 is always skipped.

The cell under review has a magenta ring in the viewer. The toolbar shows each model's prediction:

![Review mode — highlighted cell](doc_images/review_mode_highlighted_cell.png)

To label a cell that is not in the queue, click it in the viewer. Ctrl-click (Cmd-click on macOS) to select several cells and label them together. The toolbar then shows `→ clicked cell` after the queue position, and the buttons label the clicked cells:

![Review mode — multiple clicked cells](doc_images/review_mode_clicked_multiple_cells.png)

**Toolbar buttons during review:**
- **Previous** / **Next** / **Skip**: move through the queue.
- **XGB: CD8 (87%)** (blue): accept Model 1's prediction.
- **LGB: CD4 (65%)** (pink): accept Model 2's prediction. The two buttons are labelled XGB and LGB even if you chose Random Forest for either model.
- **Both: CD8 (90%)**: shown instead of the two buttons when the models agree. The percentage is the mean of the two models' confidence.
- **Avg: Treg** (green, no percentage): shown only when averaging the two models' probabilities gives a class that neither model chose. Click it to accept that class.
- **All Classes ▼**: choose any other class.
- **Done**: close review and save your labels.

The toolbar header also shows the names of any annotations containing the current cell, e.g. `◆ Tumour, Stroma`.

When you open a different image, its saved predictions are loaded. If it has none, the trained classifier is applied to it when you enter review mode, so you do not need a separate prediction step.

**Channel display.** If you have set up a channel mapping (§[4.4](#44-channel-mapping-for-review-marker-table)):
- Tick **Auto-select channels during review** to show only the channels mapped to the current cell's predicted class.
- Tick **Auto-adjust brightness/contrast of shown channels** (off by default) to also set each shown channel's display range for each cell. Leave it unticked to keep your own brightness/contrast settings.

Both checkboxes keep their setting after you close review mode or restart QuPath.

**Edit channel mapping…** (below the checkboxes) opens the [Channel Mapping editor](#44-channel-mapping-for-review-marker-table). You can keep reviewing while it is open. When you save in the editor, the current cell's channels update immediately.

The status line below it shows what is displayed for the current cell:

- `CD8T → CD8, CD3`: the channels shown for the cell's class.
- `No channels mapped for "X"`: the class has no mapping. The display is not changed.
- `No channel in this image matches "X"'s mapping — display unchanged` (red): the mapping lists channels this image does not have.
- `No channel mapping set — use Edit channel mapping… to choose channels per class.`: no mapping has been set up.

After review, click **Train** again to train with the new labels.

---

## 8. Project Prediction Summary

> **Experimental.**

**Menu:** *Extensions → SP Classify → Project Prediction Summary...*

Shows one row per project image, using the saved predictions in `<project>/celltune/image-predictions/`, and gives each image an anomaly score so you can find the images to check first. See [How the anomaly score is calculated](#anatomy-of-the-anomaly-score).

> To check images before cell segmentation, using pixel values only, see §[17 Image pixel prescreen](#17-image-pixel-prescreen-whole-image-qc-no-cells-needed).

**Table columns:** Image, Predicted, Agreements, Disagreements, Agreement %, Anomaly, Flagged.

**Filters:**
- **Flagged only**: hide images that have no flag.
- **Target class:** show only images where the chosen class has the `RARE_ENRICHMENT` flag. The list contains only classes that have this flag in at least one image. Default: **All classes**.
- **Threshold preset:** hide unflagged images whose anomaly score is below the preset: `strict` 1.5, `balanced` 0.5, `sensitive` 0.0 (default; shows every image). Flagged images are always shown. The analysis is not re-run.

**Buttons:**
- **Open Selected Image**: opens the selected image.
- **Export CSV**: saves the rows currently shown as a CSV file.

> ⚠️ **Open Selected Image discards unsaved changes in the current image without asking.** Save first (*File → Save*) if you have made changes.

**Details pane** (below the table): for the selected image, shows the anomaly score, flag reasons, rare-class enrichment and the number of cells per class.

![Project Prediction Summary](doc_images/prediction_summary.png)

### Anatomy of the anomaly score

For each image:

1. **Composition distance**: how different the image's class proportions are from the whole project (Jensen-Shannon distance).
2. **Disagreement rate**: disagreements ÷ predicted cells.
3. Both values are converted to robust z-scores across all images. These use the median and the median absolute deviation (MAD), so one extreme image does not change the scale for the others.
4. `Anomaly score = 0.65 × max(0, z_composition) + 0.35 × max(0, z_disagreement)`. Negative z-scores count as 0.

Composition has the larger weight because the disagreement rate partly depends on which two model types you chose. Use the score to rank images. It is not a probability.

**Flag reasons:**
- `RARE_ENRICHMENT`: a class that is under 1% of all cells in the project has at least 20 cells in this image and is at least 3× more common here than in the whole project.
- `COMPOSITION_OUTLIER`: composition z-score ≥ 3.
- `HIGH_DISAGREEMENT`: disagreement z-score ≥ 3.

**How to use it:**
- Sort by **Anomaly** (the default sort). Check the top rows first.
- Flagged with high disagreement: the classifier performs poorly on this image. Open it, label 10–20 cells, and retrain.
- Composition outlier with low disagreement: either a real biological difference, or a staining or segmentation problem. Check the image visually.
- Rare enrichment: check whether the cells really are that class, or are a staining or segmentation artefact.

> With fewer than 5 images, the z-scores are unreliable.

---

## 9. Intensity heatmaps

**Menu:** *Extensions → SP Classify → Intensity Heatmaps...*

Shows the mean whole-cell intensity of each marker for each cell class. Rows are classes and columns are markers (`<marker>: Cell: Mean` measurements). Use it to check that each class has high values for its expected markers, e.g. CD8 T cells high for CD8, Tregs high for FOXP3.

When the window opens, choose which whole-cell mean measurements to include:

![Select measurements for intensity heatmap](doc_images/select_measurements_for_intensity_heatmap.png)

**Colour** shows each marker's z-score across classes: red means the class is higher than other classes for that marker, blue means lower. Colours compare classes within one marker, not brightness between markers. Grey means no cells of that class had a value for that marker. **Show mean values** (ticked by default) prints the mean in each square.

![Mean marker expression per phenotype heatmap](doc_images/marker_intensity_heatmap.png)

**Image selector** (top of the window):
- **The open image** (selected by default).
- **Any other project image**: its saved data is loaded and its heatmap calculated. While the window is open, the result is kept, so choosing the image again is immediate.
- **All Images (Project Combined)**: one heatmap for the whole project. Each mean is calculated over all cells from all images, not as an average of the per-image means.

**Buttons:**
- **Export as PNG…**: saves the heatmap as shown, on a white background.
- **Export CSV…**: saves a `Class, CellCount, <marker>…` table of the mean intensities (`NA` where a class had no value).

> The heatmap needs `<marker>: Cell: Mean` measurements. If your cells do not have them, run *Analyze → Cell detection → Cell detection* in QuPath first. Classes are taken from the current cell classifications, so run a classifier or gating (§[11](#11-cell-scatter-plot--clustering--gating)) before opening the heatmap.

---

## 10. Distance measurements (spatial analysis)

**Menu:** *Extensions → SP Classify → Generate Distance Measurements...*

Adds distance measurements to each cell, for spatial analysis. It runs on the project images you select, and opens and saves each one for you.

There are three types of measurement. All three are ticked by default.

| Computation | What it writes per cell |
|---|---|
| **Detection-to-annotation signed distances** | `Signed distance to annotation <class> <unit>`: negative inside the annotation, positive outside. |
| **Cross-class centroid distances** | `Distance to detection <class> <unit>`: distance from the cell's centre to the centre of the nearest cell of each other class. |
| **Same-class nearest-neighbour distances (excludes self)** | `Distance to other <class> <unit>`: distance to the nearest other cell of the same class. |

`<unit>` is `µm` when a pixel size is available (from the image calibration or the **Pixel size:** field), otherwise `px`.

### Dialog options

- **Images**: one checkbox per project image, all ticked by default. Buttons: **All** / **None** / **Current only**.
- **Pixel size:** (µm/pixel), optional. Filled in from the open image's calibration when it has one.
  - Leave it empty to use each image's own calibration.
  - Enter a value to use it for every selected image, so results are in µm.
  - **Persist this pixel size to each image's calibration on save** (off by default): saves the pixel size into each image's calibration, so later measurements also use it. When unticked, each image's original calibration is restored after the run.
- **Skip images where all selected measurements already exist** (on by default): skips an image only if every cell already has every selected measurement. If any measurement is missing, the whole image is recalculated. Use this to resume an interrupted run. Untick it to recalculate everything, e.g. after changing classes.
- **Parallel image workers:** how many images are processed at the same time. The range is 1 to the number of processors. The default is half the number of processors, up to 4. The calculation for each image already uses all processors, so more workers mainly let images load and save while others are calculated.
  - Many images of about 10,000–20,000 cells: use more workers.
  - Images of 500,000 cells or more: use 1–2 workers. This also uses less memory.

### Running it

Click **Apply**. The log shows the progress of each image, e.g.:

```
Starting on 41 image(s)…
Using 1 parallel image worker(s) (cores=14).
[slide1.ome.tif] Loading…
[slide1.ome.tif] Skipped — all selected measurements already present.
[slide2.ome.tif] Same-class nearest-neighbour distances…
[slide2.ome.tif]   Tumour: 82770 cells in 18830 ms → Distance to other Tumour µm
[slide2.ome.tif] Saved.
```

Classes with only one cell are skipped for the same-class measurement and logged as `Skipping '<class>' (n=1)`. Each processed image is saved to the project automatically. **Close** closes the dialog.

---

## 11. Cell scatter plot — clustering & gating

**Extensions → SP Classify → Scatter Plots and Clustering...** opens a scatter plot
for unsupervised clustering. Cells are clustered on their marker measurements with
k-means or Leiden (§[11.6](#116-clustering-method-k-means-vs-leiden)) and drawn on a
2D PCA or UMAP plot. You can then name the clusters as QuPath classes. This does not
use or change the trained classifier or its training labels.

When the window opens, pick the measurements to use in the *Select Measurements for
Scatter Plot* dialog. The window then loads the open image's cells (up to the
**Sample:** cap, default 50,000) and shows *"… cell(s) loaded — click “Recompute” to
cluster."* Click **Recompute** to cluster and draw the plot.

> Clustering uses the normalisation set in **Clustering Normalisation**
> (§[4.2](#42-clustering-normalisation)). The classifier always uses raw values. Each
> marker is then z-scored over the cells being clustered. Set the normalisation before
> you open this window: a plot keeps the normalisation it was built with, and
> reopening it from the menu or clicking **New clustering session** does not update it.

### 11.1 Controls

**Top row**
- **Embedding** — `PCA` (fast) or `UMAP` (slower; often separates overlapping
  populations better). The embedding only positions the points on the plot.
  Clustering always uses the marker values, not the 2D coordinates.
- **Full UMAP** (UMAP only) — by default UMAP plots a random 20,000 of the loaded
  cells. All loaded cells are still clustered, and the status bar shows both counts,
  e.g. *"50,000 clustered · 19,432 plotted"*. Tick **Full UMAP** to plot every loaded
  cell. This is slower and uses more memory. PCA always plots every loaded cell.
- **Method** — `k-means` (default) or `Leiden` (§[11.6](#116-clustering-method-k-means-vs-leiden)).
- **Clusters (k)** — number of k-means clusters, 2–50, default 8. Shown only when
  Method = k-means. Leiden uses **Resolution** instead.
- **Sample multiple seeds** — runs the clustering 10 times from different starting
  points with a fixed seed and keeps the best result. Repeated runs with the same
  settings then give identical clusters. Applies to k-means and Leiden. When unticked,
  one faster run is made, and cluster numbers (sometimes boundaries) can change
  between runs.
- **Reduce dims (PCA)** (on by default) and **PCA comps:** (2–500, default 50) —
  when more than 50 measurements are selected, clustering runs on this number of
  principal components instead of on every measurement. This stops a marker that has
  many measurement columns (mean, median, nucleus, cytoplasm, etc.) from dominating
  the result. With 50 or fewer measurements it has no effect. When PCA is used, the
  status bar shows e.g. *"PCA: 240 → 50 comps, 87.3% variance"*. The heatmap in the
  assignment dialog (§11.3) still shows the original marker values.
- **Cluster all cells / Transfer from sample** — shown only when Method = Leiden and
  Scope = Project (§[11.5](#115-project-wide-clustering-across-images)).
- **Recompute** — runs the clustering and the embedding on the cells currently
  loaded. It does not draw new cells (use **Re-sample** for that). In project scope,
  if no sample has been drawn yet, Recompute draws one first and then clusters it.

**Scope row**
- **Scope: Current image / Project** — *Current image* (default) clusters cells from
  the open image. If the image has more cells than the **Sample:** cap, a random
  subset of that size is used, and the status bar shows *"Subsampled X of Y cell(s)"*.
  *Project* clusters a sample pooled from several images and adds an **Images…**
  button (§[11.5](#115-project-wide-clustering-across-images)).
- **Images…** (project scope only) — choose which project images to sample. This
  clears the plot and does not sample. Click **Re-sample** afterwards.
- **Sample:** (1,000–5,000,000, default 50,000) — the maximum number of cells to
  load. Applies in both scopes. Press Enter or click **Re-sample** to apply a new
  value.
- **Re-sample** — draws a new random set of cells up to the **Sample:** cap: from the
  chosen images in project scope, or from the open image in current-image scope. It
  does not cluster. Click **Recompute** afterwards.
- **New clustering session** — reopens the *Select Measurements* dialog so you can
  choose a different set of measurements, then starts a new plot. If you only close
  the window and reopen it from the menu, the previous clusters, scope and settings
  are restored without re-clustering (in current-image scope, only when the same
  image is open).

**Filter row (gating)**
- **Annotation** — enter one or more comma-separated keywords (e.g. `Tumour, Stroma`).
  Only cells whose centroid lies inside an annotation whose name or class contains a
  keyword are clustered. Leave blank to use all cells. In current-image scope, press
  Enter to re-run. In project scope each image is filtered by its own annotations
  when the sample is drawn, so click **Re-sample** after changing the keywords.
- **Within class** — cluster only cells whose current QuPath classification contains
  this text (pick from the dropdown or type). Works in both scopes and combines with
  the annotation filter. In project scope, **Assign Clusters…** then changes only
  cells of that class, so a sub-clustering only reclassifies that population.
- **Cluster markers** — a checklist of the selected measurements, all ticked by
  default. Untick markers to cluster on a smaller panel (e.g. immune markers only).
  Values are z-scored again over the cells being clustered on each run, so a
  sub-clustering is scaled to that subpopulation, not to the whole image. Tick at
  least 2 markers.

**Colour cells in image row**
- **By cluster** — colours every cell in the open image by its nearest cluster and
  writes a numeric `Cluster` measurement. If **Within class** is set, only cells of
  that class are coloured. It does not change the cell's
  classification. The colouring is removed when the window closes. In project scope
  the button reads **By cluster (all images)**: it writes `Cluster` to every cell in
  every selected image and saves each image.
- **By classification** — returns the viewer to QuPath's class colours.

**Bottom row**
- **Colour by** — `CLUSTER` (cluster number), `CLASS` (current class), or `MARKER`
  (one marker's intensity; pick the marker in **Marker:**).
- **Select: Box / Lasso** — drag on the plot to select those cells (§11.2).
- **Apply Clusters… / Assign Clusters…** — name clusters as classes (§11.3). The
  label is **Apply Clusters…** in current-image scope and **Assign Clusters…** in
  project scope.
- **Export PNG…** — saves the current plot as a PNG.

> If the status bar shows *"(UMAP unavailable — showing PCA)"*, UMAP could not start
> on this computer. Restart QuPath with the launch option
> `--add-opens=java.base/java.lang=ALL-UNNAMED` to enable it.

### 11.2 Selecting cells

- Drag a **Box** or **Lasso** to select the enclosed points.
- In `CLUSTER` colour mode, click a cluster in the legend to select all its cells.

In **current-image scope**, selection works both ways: selecting points on the plot
selects those cells in the QuPath viewer, and selecting cells in the viewer outlines
them on the plot.

In **project scope**, the sampled cells come from images that are not open, so
selecting points only highlights them on the plot. Use this to read the class or
marker intensity of a region. The viewer selection does not change.

### 11.3 Apply Clusters / Assign Clusters — assign classes to clusters

Both scopes use the same dialog. It shows one row per non-empty cluster with: a
colour swatch, the cell count, a heatmap of the cluster's mean z-scored value for
each marker (red = high, blue = low), and a dropdown. Use the high markers to decide
the name. In the dropdown, pick an existing class, type a new class name, or choose
**— skip —**.

To edit classes without closing the dialog, click **Manage Classes…** to open
[Class Control](#43-create-classes--class-control) (add, delete, merge), then click
**Refresh classes** to reload the class list into every dropdown. A class name typed
into a dropdown is created when you assign.

![Assigning classes to clusters](doc_images/assign_parent_clusters.png)

- **Current-image scope (Apply Clusters…)** — a second dialog shows the number of
  cells that will change. After you confirm, the chosen classes are written to those
  cells. Skipped clusters are not changed. Only the loaded cells are classified (at
  most the **Sample:** cap). To classify every cell in a large image, set **Sample:**
  to at least the image's cell count and click **Re-sample**, then **Recompute**,
  before applying.
- **Project scope (Assign Clusters…)** — see §[11.5](#115-project-wide-clustering-across-images).

In both scopes, the new class replaces any existing class on the assigned cells. The
extension's training labels are not changed.

### 11.4 Cluster-within-clusters (hierarchical gating)

Use the filter row to cluster inside one population (two-level phenotyping):

1. Cluster all cells on all markers → **Apply Clusters…** → assign the main classes
   (e.g. **Tumour / Immune / Other**).
2. Set **Within class: Immune**, open **Cluster markers** and tick only the immune
   markers (CD45, CD3d, CD8A, CD4, CD20, PD1, FOXP3) → **Recompute**. Only immune
   cells are clustered, on immune markers, z-scored within the immune cells.
3. Click **Apply Clusters…** again to name the subpopulations. Type names in the form
   `Immune: CD8 T` (QuPath treats `Parent: Child` as a derived class).

![Sub-clustering within the Immune class](doc_images/immune_sub_cluster.png)

Repeat for further levels. The status bar shows the active filter and marker count,
e.g. *"(12,840 cells in class “Immune”) · 7/24 markers"*.

### 11.5 Project-wide clustering across images

Project scope fits one clustering on a sample of cells pooled from the images you
choose, then applies it to every cell in those images. Cluster 3 then means the same
population in every image. (If you cluster each image separately, the cluster numbers
cannot be compared between images.) All controls in §11.1 work in project scope.
Selection on the plot only highlights points (§11.2).

**Entering project scope**

1. Click **Project**. Choose the images to sample (all are selected by default).
   Click Cancel to stay on the current image.
2. Click **Re-sample**. The extension reads each image and takes up to **Sample:** ÷
   (number of images) random cells from each, up to 50,000 in total by default. The
   status bar shows *"Sampled X cell(s) across N image(s)"*.
3. Click **Recompute** to cluster the sample and draw the plot. (Clicking
   **Recompute** straight after step 1 does steps 2 and 3 together.)

The sample is only used to fit the clusters. When you assign, every cell in every
selected image is assigned, one image at a time, so the number of images does not
limit memory use. Raising **Sample:** above 50,000 makes the fit slower.

**Working with the cohort sample**

- **Colour by → MARKER** shows which clusters are high in which marker.
- **Within class** sub-clusters one population across all images. **Assign
  Clusters…** then changes only cells of that class.
- **Cluster markers** fits on a smaller panel.
- **Recompute** clusters the current sample again. To use different images, click
  **Images…** and then **Re-sample**. To change the sample size, change **Sample:**
  and click **Re-sample**. Then click **Recompute**.

**Assigning across the cohort**

Click **Assign Clusters…**. The dialog from §11.3 opens. After you confirm, each
selected image is opened in turn, every matching cell is given its cluster's class,
and the image is saved. Progress is shown in the status bar. (For how cells outside
the sample are assigned, see §[11.6](#116-clustering-method-k-means-vs-leiden).)

> **This changes every selected image.** Assigning replaces the class on the assigned
> cells and saves each image. Training labels are not changed. The open image updates
> immediately.

> **Annotation filter.** In project scope the **Annotation** filter limits which
> cells are sampled. With k-means, or Leiden **Transfer from sample**, **Assign
> Clusters…** and **By cluster (all images)** assign every cell in each image that
> passes **Within class**, including cells outside the matching annotations.

> **Staining differences between images.** Normalisation is applied per marker, not
> per image. If one slide is stained brighter than the others, its cells can fall
> into different clusters. Check the per-image intensity distributions before
> pooling.

**Leiden in project scope: Cluster all cells / Transfer from sample**

With **Method = Leiden** and **Scope = Project**, two options appear next to
**Method**:

- **Cluster all cells** (default) — clusters every cell in every selected image
  together, not just the sample. This is slower and uses more memory.
- **Transfer from sample** — clusters only the sample. Each other cell takes the most
  common cluster among its 15 nearest cells in the sample. This is faster.

With **Cluster all cells**, the full run is started by **By cluster (all images)**:

1. Click **Recompute** to preview the clusters on the sample.
2. Click **By cluster (all images)** and confirm. The extension:
   - counts the cells first. If there are more than 50,000,000, it asks you to
     confirm again;
   - shows each step in the status bar: *Pooling 12/40 images → Building kNN graph… →
     Running Leiden… → Writing 12/40 images*;
   - writes a `Cluster` measurement to every cell and saves each image.
     Classifications are not changed;
   - shows a **Cancel** button. Cancelling stops before the next image. Images
     already written keep their new `Cluster` values.

   When it finishes, the legend, the image colouring and the **Assign Clusters…**
   dialog show the clusters found across all cells. The plot itself still shows the
   sample.
3. Click **Assign Clusters…** to name the clusters. Classes are assigned from the
   written `Cluster` values.

If you click **Assign Clusters…** without step 2, or after a new **Recompute**, cells
are assigned by transfer from the sample instead.

If the neighbour search is not accurate enough (below 95% recall), the run stops and
an error dialog says that no `Cluster` measurement was written. Existing `Cluster`
values are kept. Try different markers or more cells. The same check runs for Leiden
on a single image or a project sample: if the status bar shows *"Leiden preview: ANN
recall too low — try more cells / different markers."*, no clusters were made.
Increase **Sample:** or change the ticked **Cluster markers** and click **Recompute**.

> **Comparison with scanpy.** Results are similar to `sc.tl.leiden` in Python but not
> identical. This extension uses a different quality function (CPM rather than
> modularity) and weights the neighbour graph by shared neighbours (Jaccard) rather
> than UMAP connectivities. The same **Resolution** value can give slightly different
> cluster boundaries in scanpy.

> **Citing.** If graph-based clustering is central to your analysis, cite the
> **Leiden algorithm** (Traag, Waltman & van Eck, *Sci. Rep.* 2019), **scanpy** (Wolf,
> Angerer & Theis, *Genome Biol.* 2018), whose scale → PCA → neighbours → Leiden steps
> this follows, and **HNSW** (Malkov & Yashunin, *IEEE TPAMI* 2020), used for the
> neighbour graph. Full citations and the licences of the bundled libraries (CWTS
> `networkanalysis`, jelmerk `hnswlib-core`) are in the
> [README acknowledgements](README.md#acknowledgements).

### 11.6 Clustering method: k-means vs Leiden

**Method** (§11.1) selects the clustering algorithm. All other controls work the same
for both methods.

- **k-means** (default) splits cells into exactly **k** clusters (**Clusters (k)**,
  2–50, default 8). It works best when populations are of similar size.
- **Leiden** links each cell to its 15 nearest cells (by marker values) and finds
  groups of closely linked cells. You set **Resolution** instead of a cluster count,
  and the number of clusters comes from the data. It is better than k-means at
  keeping small or unevenly sized populations as separate clusters. This is the
  method used by scanpy, scimap and SPACEc.

![Leiden clustering of cells in the scatter plot, coloured by community](doc_images/leiden_clustering.png)

**Controls when Method = Leiden**

- **Resolution** (0.1–3.0, default 1.0) — replaces **Clusters (k)**. Higher values
  give more, smaller clusters; lower values give fewer, larger clusters. After
  **Recompute** the status bar shows the number of clusters found, e.g.
  *"7 cluster(s)"*. To get more or fewer clusters, raise or lower the resolution
  and click **Recompute** again.
- **Sample multiple seeds** — see §11.1. When ticked, Leiden runs 10 times with a
  fixed seed (42) and keeps the best result. When unticked, cluster numbers, and
  sometimes boundaries, can change between runs.

The neighbour count (15) and the edge weighting (shared-neighbour Jaccard) are fixed
and cannot be changed in this version.

**How cells outside the sample are assigned (project scope)**

- **k-means:** each cell joins the cluster with the closest mean.
- **Leiden, Transfer from sample:** each cell takes the most common cluster among its
  15 nearest cells in the sample.
- **Leiden, Cluster all cells:** all cells are clustered together, so no assignment
  step is needed (§[11.5](#115-project-wide-clustering-across-images)).

---

## 12. Exporting results

### 12.1 Cell table export

**Menu:** *Extensions → SP Classify → Export → Cell Table...*

For each selected image, writes `<ImageName>.csv` to the folder you choose, with one row per detection:

| Column | Notes |
|---|---|
| `Image` | Source image name |
| `CellID` | QuPath cell UUID |
| `CentroidX_um` / `CentroidY_um` | Centroid in microns, 2 decimals (pixel × calibration if no micron value) |
| `Area_um2` | Cell area in µm², 2 decimals |
| `Classification` | Current class (empty if unclassified) |
| `ParentAnnotations` | All ancestor annotations, joined with `; ` |
| `ContainingAnnotations` | Every annotation whose outline contains the cell centroid, joined with `; `. Includes overlapping annotations that the hierarchy does not record as parents |
| `Geometry_um` / `Geometry_px` | *(optional)* WKT `POLYGON` of the cell outline, in microns or pixels. Written only when **Export cell polygons (geometry)** is ticked |
| feature columns | One column per measurement **or text field** you tick in the export dialog |

Before exporting, the **Select Columns for Cell Table Export** dialog opens. It works like *Select Features*: search box, prefix dropdown, **Select Prefix** / **Clear Prefix**, **Select All** / **Clear All**. The whole-cell mean measurements and any distance measurements are ticked by default. Numeric measurements are listed first, followed by text fields such as `CN Class`. To add cell outlines, tick **Export cell polygons (geometry)** and choose **Microns (µm)** or **Pixels** under **Units**. Any value a cell does not have is written as `NA`.

### 12.2 Ground truth export & import

Ground-truth files hold your labelled cells **and** their feature values, so you can reuse labels in other projects or on other computers.

#### Export

**Menu:** *Extensions → SP Classify → Export → Ground Truth...*

Header (commented):
```
# SP Classify Ground Truth Export
# Image: my_image.ome.tiff
# Exported: 2026-06-02T14:30:45
Image,Label,CentroidX,CentroidY,Feature1,Feature2,...
```

Exports raw feature values (the values the classifier uses) for labelled cells only.

In multi-class mode the export includes labels from the current image and all other project images. In **binary mode** use **Export → Active Binary Ground Truth...** instead. It exports only the active marker and includes training rows imported from other projects, so the file can be moved between projects without losing rows.

#### Import

**Menu:** *Extensions → SP Classify → Import → Ground Truth...*

After choosing the CSV, pick one of two modes:

1. **Spatial Match** (per image) — each imported row is matched to the nearest detection by centroid distance, up to a maximum distance you set (default 20 px). Rows with no detection within that distance are skipped. Use this to re-import labels onto the **same** image they were exported from.
2. **Training Data Only** (cross-project) — imports the feature values and labels without matching them to cells. Use this when the source image is not in the current project. The rows are used in the next training run in the same way as labels on cells. The sidebar shows the count as `Imported rows: N`.

For binary mode use **Import → Active Binary Ground Truth...**. It has the same two modes but applies only to the active marker.

> Ground truth can only be exported and imported as single CSV files. There is no ZIP bundle option.

---

## 13. Utility scripts

*Extensions → SP Classify → **Utility Scripts***

Tools for common cleanup tasks. Each one asks for its settings, then reports what it changed.

### 13.1 Filter Cells by Size & Circularity

Removes cell detections from the **current image** that fall outside size and shape limits. The dialog has **Min** and **Max** boxes for **Cell area (µm²)** and **Circularity (0–1)**. It opens with Max area = 500 and Min circularity = 0.7. Clear a box to remove that limit. A cell is removed if it breaks any limit (e.g. `area > 500` **or** `circularity < 0.7`). The tool uses the first measurement whose name contains "area" (or "circularity"). Check which one that is in your cell measurements: it may be a nucleus measurement rather than a whole-cell one. Cells missing either measurement are kept. The number of cells to be removed is shown before anything is deleted.

### 13.2 Resolve Hierarchy

Rebuilds parent/child relationships from object outlines, the same as the `resolveHierarchy()` script command. Choose **Current image** (applied immediately) or **All project images** (asks to confirm, then resolves and saves every image). The open image updates immediately; other images are processed in the background.

### 13.3 Delete Measurements by Keyword

> ⚠️ **Destructive and cannot be undone.** Check the keyword against your measurement names. A short keyword can match more columns than you intend.

Removes every detection measurement whose name contains a keyword. Matching ignores case unless you tick **Case sensitive**. Choose **Current image** or **All project images**. Before deleting, the extension lists the matching columns and asks you to confirm. If nothing matches, nothing is deleted. With **All project images**, every image is saved (the open image first, the rest in the background).

### 13.4 Import GeoJSON Objects

> ⚠️ **For small-to-medium GeoJSON files only.** This importer loads the whole file into QuPath's memory. Very large files (hundreds of MB or millions of objects) can run out of memory and crash QuPath. For those, use the headless pipeline: [github.com/BioimageAnalysisCoreWEHI/import_large_geojson](https://github.com/BioimageAnalysisCoreWEHI/import_large_geojson).

**Menu:** *Utility Scripts → [TEST] Import GeoJSON Objects...*

Imports annotations and detections from a `.geojson` or `.geojson.gz` file into the **current image**. Options (both off by default): **Clear existing objects first**, and **Resolve hierarchy after import**. The second can take a long time with many objects. Annotations are added and locked first, then detections, and the image is saved.

### 13.5 Export Annotation Regions

> ⚠️ **Single image, small-to-medium regions.** For very large regions or exports from a whole project, the headless pipeline on HPC is much faster: [github.com/BioimageAnalysisCoreWEHI/export_large_annotation_regions](https://github.com/BioimageAnalysisCoreWEHI/export_large_annotation_regions).

**Menu:** *Utility Scripts → [TEST] Export Annotation Regions...*

Exports annotations from the **current image** as OME-TIFFs. Pixels outside each annotation's outline are set to 0. Enter annotation names separated by commas, or leave blank to export all annotations. Defaults: **Downsample** 1.0, **Tile size (px)** 512, **Writer threads** = number of CPU cores (maximum 32), **Compression** LZW, **BigTIFF** on, **Build pyramid** on. Each region is saved as `<image>__<annotation>.ome.tif` in the folder you choose, and a notification reports how many succeeded. Requires QuPath's Bio-Formats extension (included and loaded by default).

### 13.6 Reset Project State

> ⚠️ **Destructive.** Deletes everything the extension has saved for this project. Use it to start again, e.g. after **copying a project** to try different classifier settings: the `celltune/` folder is copied with the project.

Deletes the project's `celltune/` folder: all labels and per-image label files, trained classifiers (multi-class **and** binary) and predictions, feature selection, normalisation, marker table, composite rules, and sampling/review state. It also resets the current session so the old state is not saved again.

**Backup:** before deleting, the extension writes `celltune_backup_<timestamp>.zip` to the project folder. To undo the reset, unzip it into the project folder. This recreates `celltune/`. To confirm the reset, type `RESET`.

**Images and detections are kept.** The extension's ground-truth **label points** and the **cell classifications** (predictions) are stored in each image's `.qpdata`, not in `celltune/`, and are kept by default. To remove them too, tick **"Also clear SP Classify label points and all cell classifications from every image"**. This deletes classified point annotations and clears every cell's classification in **all** project images, and saves each image. Tissue/region annotations and unclassified points are never changed.

### 13.7 Lock All Annotations

Locks every annotation so it cannot be moved or edited by mistake. Choose **Current image** or **All project images** (asks to confirm, then saves every image). A notification reports how many annotations were locked.

---

## 14. Reference: every setting in the sidebar

| Control | Default | What it does |
|---|---|---|
| **Rounds** | 500 | Maximum boosting rounds (50–1000). With **Early stopping** ticked, training stops sooner when the model stops improving, so a high value costs little. With it unticked, every round is used; lower the value. |
| **Max depth** | 6 | Maximum tree depth (2–15). Higher values model more complex marker combinations but overfit more easily. |
| **CPU threads** | 0 (all) | Number of CPU threads training uses; 0 = all cores. Lower it to keep QuPath responsive during training. Remembered between sessions. See §5.3. |
| **Images at once** | 1 | Number of images classified at the same time by **Apply to which images...** after training (1–8). Each image is loaded whole, so higher values use more memory. Does not affect training. |
| **Model 1** | XGBoost | First model type. |
| **Model 2** | LightGBM | Second model type. Use a different type from Model 1, so the two models disagree on uncertain cells. |
| **Pool labels from all images** | ✅ | Train on labelled cells from every project image. Always on in binary mode. |
| **Enable data balancing** | ✅ | Resample the training set to balance the classes. Untick to hide **Strategy**. |
| **Strategy** | SMOTE + Tomek | Resampling method — see the §5.4 table. |
| **Auto-tune hyperparameters** | ❌ | Searches for better model settings. Cost = 2 × Trials × CV folds model fits (200 at the defaults). This can take several hours on a panel with many features. |
| **Trials** | 20 | Settings combinations tried per model (5–100). Shown only when Auto-tune is ticked. |
| **CV folds** | 5 | Cross-validation folds used to score each combination (2–10). Shown only when Auto-tune is ticked. |
| **Early stopping** | ✅ | Stops adding rounds when the score on held-out cells has not improved for 20 rounds. |
| **Train/val metrics** | ✅ | Produces the **Training Metrics** report. Adds about a third to training time. Untick to train faster without the report. |
| **Show top 10 feature importance after training** | ✅ | Opens the SHAP feature-importance plot after training. |
| **Auto-prune features (drop near-constant & redundant)** | ✅ | Before training, removes features that are almost constant or highly correlated with another feature of the same marker. The 5 highest-variance features of each marker are always kept. Runs only when more than 20 features are selected. Image measurements are not changed. See §[4.1](#41-select-features). |
| **Restrict to features shared with imported data** | ❌ | Train only on features that also exist in the imported ground-truth columns (names matched ignoring case). |
| **Sample current image only** | ❌ | Sample and review cells from the open image only. |
| **Filter by annotation keywords** | (blank) | Comma-separated keywords. Only cells inside annotations whose names contain a keyword (any case) are sampled for review. |
| **Apply to which images...** | (all) | Choose the project images the trained classifier is applied to. The button label shows how many are selected. |
| **Manual Label Mode** | — | Opens the floating labelling toolbar. |
| **Train** | — | Starts training. Needs at least 10 labelled cells. |
| **Agreement Confusion Matrix** | (disabled) | Shows how often the two models agree, per class. Available after training. |
| **Training Metrics** | (disabled) | Per-class precision, recall and F1 on a 20% held-out split. Available after training with **Train/val metrics** ticked and at least 20 labelled cells. |
| **Feature Importance...** | (disabled) | SHAP top features per class. Available after training. |
| **Enter Review Mode** | (disabled) | Samples cells where the two models disagree, for you to review. Available once predictions exist. |

### 14.1 Reference: preferences

Under **Edit → Preferences → SP Classify**.

| Preference | Default | What it does |
|---|---|---|
| **Enable SP Classify extension** | ✅ | Turns the extension off without uninstalling it. |
| **XGBoost histogram bins** | 0 | Leave at 0. See below. |
| **Use batch-corrected values** | ❌ | The same setting as **Use batch-corrected values in clustering + ML (streamed, no columns)** in the Batch Normalisation dialog. See §[19.4](#194-how-its-applied). |

**XGBoost histogram bins.** Leave this at 0 (= 256 bins, the most accurate setting). Lower values make XGBoost training faster (128 about 2×, 64 about 2.5×) but change some predictions. To try a lower value, train once at 0 and once at 128, export the cell table each time, and compare the Training Metrics and the class columns. The training log records the value used as `XGB max_bin`.

---

## 15. Reference: every SP Classify menu item

All under *Extensions → SP Classify*, in menu order.

| Item | Requires | Action |
|---|---|---|
| Binary Classifiers... | Project | Opens the binary classifier manager: create, open or delete one classifier per marker. |
| Composite Classification... | Project + ≥1 trained binary classifier | Applies trained binary classifiers and assigns composite labels. |
| Class Control... | Project | Add, delete, merge and undo-merge classes. |
| Channel Mapping (Review Display)... | Open image | Sets which image channels review shows for each class. Saved per project, with CSV import and export. See [§4.4](#44-channel-mapping-for-review-marker-table). |
| Select Features... | Project | Choose which measurement columns are used for training. |
| Clustering Normalisation | Project | Sets an arcsinh or square-root transform per feature, with a shared cofactor, for clustering only. The classifier uses raw values. See §[4.2](#42-clustering-normalisation). |
| Project Prediction Summary... | Project | Cohort QC: anomaly score and flags for each image. See §[8](#8-project-prediction-summary). |
| Intensity Heatmaps... | Open image with detections | Heatmap of mean marker intensity per class (z-score colours), for one image or the whole project. PNG and CSV export. See §[9](#9-intensity-heatmaps). |
| Image Pixel Prescreen... | Project | Whole-image QC without cells: per-channel pixel statistics compared across the project, with flags for background-heavy, saturated, weak-signal and intensity-outlier images. CSV export. See §[17](#17-image-pixel-prescreen-whole-image-qc-no-cells-needed). |
| Scatter Plots and Clustering... | Open image with detections | PCA/UMAP scatter plot with k-means or Leiden clustering, annotation and class gating, and cluster → class assignment. **Scope** switches to clustering across several images. See §[11](#11-cell-scatter-plot--clustering--gating). |
| Cellular Neighborhoods... | Open image with cells in at least 2 classes | Groups cells by the cell types around them (k-means on neighbourhood composition), for one image or the whole project. See §[18](#18-cellular-neighborhoods-spatial-micro-environments). |
| Batch Normalisation... | Project | Aligns each marker's intensity across images (UniFORM). Fit and QC, then use the corrected values in clustering and the classifier, or write them as `(batchnorm)` columns. See §[19](#19-batch-normalisation-uniform). |
| Generate Distance Measurements... | Project | Distances to annotations (signed), to other classes, and to the nearest cell of the same class, for the selected images. See §[10](#10-distance-measurements-spatial-analysis). |
| Export → Cell Table... | Open image with detections | One CSV per selected image. See §[12.1](#121-cell-table-export). |
| Export → Ground Truth... | Open image with labels (multi-class) | CSV of labels and feature values that you can import into another project. |
| Export → Active Binary Ground Truth... | Binary mode active + open image with labels | As above, for the active marker only. |
| Import → Marker Table... | Open image | Loads a cell type → markers CSV used to switch channels during review. The Channel Mapping editor ([§4.4](#44-channel-mapping-for-review-marker-table)) is the recommended way to edit it. |
| Import → Ground Truth... | Open image (multi-class) | Imports labels by spatial match, or as training data only. |
| Import → Active Binary Ground Truth... | Binary mode active + open image | As above, for the active marker only. |
| Utility Scripts → Filter Cells by Size & Circularity... | Open image with cells | Removes cells outside optional area and circularity limits, in the current image. See §[13.1](#131-filter-cells-by-size--circularity). |
| Utility Scripts → Resolve Hierarchy... | Open image or project | Rebuilds parent/child relationships between objects, for the current image or the whole project. See §[13.2](#132-resolve-hierarchy). |
| Utility Scripts → Lock All Annotations | Open image or project | Locks every annotation, in the current image or in all project images, so it cannot be moved or edited by accident. See §[13.7](#137-lock-all-annotations). |
| Utility Scripts → [TEST] Import GeoJSON Objects... | Open image | Imports objects from a GeoJSON or gzipped GeoJSON file into the current image. Small to medium files only. See §[13.4](#134-import-geojson-objects). |
| Utility Scripts → [TEST] Export Annotation Regions... | Open image | Exports annotation regions from the current image as polygon-masked OME-TIFF files. One image, small to medium regions only. See §[13.5](#135-export-annotation-regions). |
| Utility Scripts → Delete Measurements by Keyword... | Open image or project | **Destructive:** deletes detection measurements whose names contain a keyword. Shows the matches and asks you to confirm. See §[13.3](#133-delete-measurements-by-keyword). |
| Utility Scripts → Reset Project State... | Project | **Destructive:** deletes the project's `celltune/` folder (labels, models, predictions, settings). Writes a backup zip first and asks you to type `RESET`. Can also clear SP Classify label points and cell classifications from every image. See §[13.6](#136-reset-project-state). |
| How to Cite... | Nothing (always available) | Shows the references to cite for SP Classify and for the methods you used. |

---

## 16. Project directory layout

Everything the extension writes is under `<project>/celltune/`:

```
celltune/
├── classifier-state.json         # Multi-class model (features, classes, models, labels, normalisation)
├── composite-rules.json          # Saved composite classification rules
├── marker-table.json             # Channel mapping for review (§4.4)
├── binary-registry.json          # List of binary classifiers and their state files
├── batch-shifts.json             # Batch normalisation fit (§19)
├── labels_backup_YYYYMMDD_HHMMSS.json   # Copy of the labels, saved before each Train
│
├── image-labels/                 # Multi-class labels, one JSON per image
│   ├── slide1.json               #   { "<cellId>": "T-Cell", ... }
│   └── ...
│
├── binary-image-labels/<marker>/ # Same per-image JSON, one folder per binary marker
│   ├── CD3/slide1.json
│   └── ...
│
├── binary/                       # Binary classifier state files
│   ├── CD3.json
│   ├── CD8.json
│   └── ...
│
├── image-sampled/                # Cell IDs already sampled for review
├── image-predictions/            # Predictions for each image, used by Project Prediction Summary
├── logs/                         # Training logs (last 20 kept)
```

All files are JSON. You can put `celltune/` under version control (for example git) to share labels and review history.

---

## 17. Image pixel prescreen (whole-image QC, no cells needed)

> **Experimental.**

**Menu:** *Extensions → SP Classify → Image Pixel Prescreen...*

Run this at the start of a project, before segmentation. It reads a low-resolution copy of every image, measures pixel intensities for each channel, and flags images that differ from the rest of the project: mostly background, saturated, weakly stained, or unusually bright or dim. Use it to decide which images to fix, exclude, or label more heavily later. It does not need cells. The [Project Prediction Summary](#8-project-prediction-summary) does a similar check after classification.

![Image pixel prescreen](doc_images/pixel_prescreen.png)

### How it works

1. Each image is read at the pyramid level closest to 2048 px on its long edge, so every image is compared at the same size. Up to 4 images are read at once.
2. Channels are matched across images by name.
3. Statistics are calculated for each channel (table below), including a sharpness measure (**focus**).
4. The image-level values, and the `p99` brightness of each channel that has signal, are converted to robust z-scores across the project: `0.6745 × (value − project median) / MAD`. This is the same method as §8.
5. Fixed threshold rules give each image a **verdict**, zero or more **flags**, and a written **review**.

### What each statistic means

Per channel, over all pixels of the low-resolution image:

| Statistic | What it tells you |
|---|---|
| **median** | Middle pixel value. Used for sorting and comparison because single bright pixels do not change it. |
| **mean** | Average pixel value. Single very bright pixels raise it. |
| **std** | Standard deviation: the spread of pixel values. |
| **min / max** | Lowest and highest pixel value. `max` is shown but not used for flags, because one bright pixel sets it. |
| **p1 / p99** | 1st and 99th percentiles. `p1` is the background level and `p99` the signal level. Single extreme pixels do not change them. |
| **saturation fraction** | Fraction of pixels at or above 99.9% of the highest value the file can store (255 for 8-bit, 65535 for 16-bit). Measures clipping (over-exposure). `n/a` for floating-point images. |
| **Otsu threshold** | Automatic cut-off between background and foreground, calculated from the channel histogram. Used by the next two rows. |
| **background fraction** | Fraction of pixels below the Otsu threshold. |
| **foreground coverage** | 1 − background fraction: how much of the channel is signal. Low values mean a lot of background. |
| **dynamic range** | `p99 − p1`. Close to zero for flat, weak or empty channels. |
| **Laplacian variance (focus)** | Sharpness measure (higher = sharper). It also depends on brightness, so compare it only within a project. |

Image-level (calculated across channels):

| Statistic | What it tells you |
|---|---|
| **empty fraction** | Fraction of pixels below the Otsu threshold in **every** channel. The best measure of how much of the slide is glass or background. |
| **focus** | The highest per-channel focus value (the sharpest channel). Shown only; it never flags an image, because it changes with brightness as well as sharpness. |
| **intensity z** | The largest `p99` z-score among channels with signal. Sets the `INTENSITY_OUTLIER` flag. |

### Verdicts, flags, and the score

Each image gets one **verdict** and zero or more **flags**. Default thresholds (z = robust z-score):

| Verdict / flag | Set when |
|---|---|
| `BACKGROUND_HEAVY` | mean foreground-coverage z ≤ −2.5, **or** empty-fraction z ≥ 2.5 |
| `SATURATED` | highest channel saturation fraction ≥ 1% **and** its z ≥ 3.0, **or** saturation fraction ≥ 5% whatever the other images show |
| `WEAK_SIGNAL` | median dynamic-range z ≤ −2.5 |
| `INTENSITY_OUTLIER` | the `p99` z of a channel with signal is ≥ 2.5 or ≤ −2.5 (brighter or dimmer than the project) |
| `OK` | none of the above |

> Only channels with a median foreground coverage of at least 5% across the project are checked for intensity outliers. Channels with almost no signal are skipped, so they cannot produce false flags. Focus is shown but never flags an image.

The **Score** is the sum of the positive deviations behind the flags. A higher Score means the image is more unusual for the project. The table is sorted by Score by default.

**Table columns:** Image, Verdict, Score, Foreground %, Empty %, Max sat %, Dyn. range, Focus, Intensity z, Flagged. Tick **Flagged only** to hide unflagged images.

The **review pane** below the table explains the result for the selected image, for example:

> TRMhi_284_4 — Intensity outlier
> • Ly6G_S8 - Cy5_AF brightness (p99) 1246.00 is brighter than the cohort (median 220.00, +11.2 MAD).
> Suggested action: review / normalize — intensity differs from the cohort (may challenge ML).

This is followed by a table for each channel (median | p99 | foreground% | dyn.range | sat% | focus).

**Buttons:**
- **Open Selected Image** opens the image without saving the current one.
- **Export CSV** writes one row per image: image-level columns (including `MaxFocus`, `MaxFocusZ`, `MaxIntensityZ`, `MaxIntensityChannel`), then a block of columns for each channel (including `LaplacianVariance`).
- **Close**.

### How to read it

- **Sort by Score** (the default) and check the top rows first.
- **Background-heavy**: mostly glass or empty. Exclude the image, re-acquire it, or crop it to the tissue.
- **Saturated**: a channel is clipped. Fix the exposure, or leave that channel out of intensity-based analyses.
- **Weak signal**: a flat, low-contrast image. Check the staining or exposure.
- **Intensity outlier**: a channel with signal is much brighter or dimmer than in the other images. The review pane names the channel. The classifier may find these images harder. Consider batch normalisation (§19) or extra labelling, and check the staining batch and acquisition settings.
- **Focus** (column and per channel): sort on it to find blurred images. It is not a verdict; low focus often means only that the image is dim.
- **OK**: pixel statistics are within the normal range for the project.

> **Caveats.** With fewer than about 5 images, the z-scores are unreliable. Saturation is measured against the storage bit depth, so a 12-bit image stored as 16-bit is compared with 65535. Floating-point images show saturation as `n/a`. Up to 4 images are read at once, each at about 2048 px on the long edge with all channels, so panels with many channels need more memory. This size cannot be changed in the dialog.

---

## 18. Cellular neighborhoods (spatial micro-environments)

**Menu:** *Extensions → SP Classify → Cellular Neighborhoods...*

Cellular neighborhoods (CNs) group cells by the cell types around them, not by their own type. Each cell is described by the mix of cell types in its local window. These mixes are clustered into recurring micro-environments, such as tumour core, tumour–stroma interface or immune niches. This is the method of Schürch et al., "Coordinated Cellular Neighborhoods Orchestrate Antitumoral Immunity at the Colorectal Cancer Invasive Front," *Cell* 2020 ([full citation & acknowledgement in the README](README.md#acknowledgements)). If you use this feature, please cite that paper.

Use this when cells of the same type behave differently depending on where they are, for example CD8 T cells inside tumour compared with CD8 T cells at the invasive margin. Instead of drawing tumour, stroma and interface regions by hand, k-means finds recurring neighbourhood compositions and labels every cell with one. You get a map in the viewer and a `CN` value on every cell, from which you can calculate the fraction of each image in each CN and compare across the cohort.

Results are written as new measurements (see §18.5). Cell classifications are not changed. You need cells that already have classifications, either from running the classifier or from an import.

### 18.1 When to use it

- You want to find **tissue architecture** (tumour, stroma, interface) or **immune micro-environments** (an activated-CD8 niche, a Treg-rich area) that a per-cell phenotype does not show.
- You want a **per-image value** that you can compare across a cohort, for example "the fraction of each patient's tissue in the activated-CD8 niche", for group comparisons.

### 18.2 How the clusters are computed

The same four steps run for one image or the whole project:

1. **Neighbour window**: for every cell, find the cells around it, using one of three modes. A cell is never counted as its own neighbour; **Include centre cell in its own window** adds it back (step 2).
   - **k nearest neighbours**: the window is a fixed number of cells (**window (cells)**, default 10, range 2–100). With **Include centre cell in its own window** ticked, a window of 10 is the cell itself plus its 9 nearest neighbours, as in Schürch et al. With it unticked, it is the 10 nearest neighbours.
   - **within radius**: every cell within a set distance (default 50, range 5–500; µm if the image is calibrated, otherwise pixels).
   - **Delaunay triangulation**: neighbours are the cells directly connected to it in a triangulation, so the window size follows local cell density. Long connections across empty space are removed. Choose **max edge** for a fixed limit (default 50, range 1–2000; µm if calibrated, otherwise pixels) or **auto (Q3+1.5·IQR)** to set the limit separately for each image from its own connection lengths. A cell with no remaining connections gets `CN = -1`.

2. **Composition vector**: each window becomes a list of cell-type fractions (the proportion of Tumour, CD4 T, Treg, … in the window), using only the cell types you tick. With **Include centre cell in its own window** ticked (the default, as in the paper), the cell's own type is counted too. Cells of unticked types, unclassified cells and ignored classes are not counted. A window with no counted cells gets `CN = -1` and is left out of clustering.

3. **k-means clustering**: the composition vectors are clustered into **Number of CNs** groups. Each group is one CN. Each cell's CN number (starting at 1) is written to the `CN` measurement; empty windows get `-1`. With **Sample multiple k-means seeds (more reproducible)** ticked (the default), k-means runs 10 times and keeps the best fit.

4. **Interpretation**: the mean composition of each CN is used for the enrichment heatmap and the diversity overlay.

> **Standardize compositions before clustering** has the largest effect on results. Off (default, as in the paper): clusters separate the main tissue structure (tumour, stroma, interface). On: each cell type is scaled equally, so rare immune populations get their own clusters, but tumour and stroma merge into one or two large clusters. To get both, tick it, set **Number of CNs** to 12–15, and merge duplicate tumour clusters afterwards (§18.5).

> A single k-means run depends on its random starting point. On test data, agreement with the published neighbourhoods varied a lot between starting points. **Sample multiple k-means seeds (more reproducible)** (on by default) runs k-means 10 times and keeps the best result, so repeated runs give the same answer. Untick it for a faster single run while you try out settings.

### 18.3 Scope: current image vs whole project

**Scope**, at the top of the dialog, sets what is clustered:

- **Current image**: clusters every non-empty window in the open image. Use it to try settings on one image.
- **Whole project**: fits one model across the images you choose, then writes a CN to every cell in every image. A CN number means the same micro-environment in every image (CN 3 is the same everywhere), so you can compare images. It runs in two passes and does not load the whole project into memory at once:
  1. **Sample (fit):** take a random sample of windows, drawn evenly from each selected image, and fit k-means on it. **Sample windows for fit** (default 50,000, range 1,000–5,000,000) sets how many windows are used. Every cell is still assigned afterwards.
  2. **Assign:** for each image, calculate every cell's composition, assign it to the nearest CN, write the `CN` measurement, and save the image.

  Selecting **Whole project** shows **Choose images…**, **Add project…**, **Sample windows for fit** and **Parallel workers** (§18.6).

#### Clustering more than one project together

To fit one model across several QuPath projects (for example two staining batches), click **Add project…** (next to **Also cluster projects:**) and select the other project's `project.qpproj`. All images in each added project are included, together with the images you selected in this project. Each image's results are saved in its own project. No files are copied between projects. **Clear** removes the added projects.

> Projects clustered together must use the same cell class names, because compositions are matched by class name. Separately stained cohorts can also differ by batch: compare per-project CN fractions, and consider **Standardize compositions before clustering** (§18.2). The cell-type list comes from the open image, so open a typical image before you run.

### 18.4 Running it — step by step

![The Cellular Neighborhoods dialog](doc_images/cellular_neighbourhoods.png)

*The dialog set up for a whole-project run. The current version has an extra option, **Sample multiple k-means seeds (more reproducible)**, and the kNN control is labelled **window (cells)**.*

1. Open *Extensions → SP Classify → Cellular Neighborhoods...*. Choose **Scope**. For **Whole project**, click **Choose images…**, and **Add project…** to include other projects.
2. Choose the **Neighborhood window** (see §18.2). Use **within radius** for a fixed physical distance on calibrated images, or **Delaunay triangulation** when cell density varies a lot.
3. Set **Number of CNs** (default 10, range 2–30). Fewer gives broader regions. More gives finer regions, some of which you may need to merge.
4. Tick the **Cell types** to include (**All** / **None** tick or untick every type). Leave out debris and ignored classes.
5. Set the options: **Include centre cell in its own window** (leave ticked to match the paper), **Standardize compositions before clustering** (§18.2), **Sample multiple k-means seeds (more reproducible)** (leave ticked), **Show enrichment heatmap after run**.
6. **Pixel size** (µm/pixel, optional) is filled in from the image calibration. For uncalibrated images, set it if you want the radius in µm.
7. For project scope, check **Sample windows for fit** (default 50,000) and **Parallel workers** (default: number of CPU cores minus 1, up to 8; see §18.6).
8. Click **Run**. The log shows progress. In project scope, each image logs a `sampled …` line and then a `CN assigned …` line; lines from different workers are mixed together.

### 18.5 The enrichment heatmap — reading, naming, merging

The CN-by-cell-type enrichment heatmap opens after a run if **Show enrichment heatmap after run** is ticked, or when you click **Show heatmap**:

- **Rows** = CNs, with cell counts and % of all cells. **Columns** = cell types.
- **Numbers** = each CN's mean fraction of each cell type (shown when **Show mean fractions** is ticked).
- **Colour** = z-score of each cell type across the CNs. Red means more of that cell type than in the other CNs, and blue means less, so a cell type that is low in absolute terms but higher than in other CNs is still shown in red. Read the colour (which type defines the CN) together with the number (how much of it there is).

![CN enrichment heatmap for a 10-CN project run](doc_images/cn_enrichment_heatmap_10_CN_whole_project_500k.png)

*A 10-CN result for a whole project. CN 8 and CN 1 are mostly tumour (32% and 20% of cells). CN 4 is stroma/other. CN 5, 7 and 10 are small immune-rich neighbourhoods (1–3% of cells). CNs 1, 2, 8 and 9 are all tumour-dominated; give them the same name to merge them (below).*

**Name / merge:** type a name next to each CN and click **Apply names**. Results appear as columns in the detection measurement table and in cell-table exports (§[12.1](#121-cell-table-export)). None of them change cell classifications.

| Result | Written when | Stored as | Key | Values |
|---|---|---|---|---|
| Raw cluster id | **Run** | numeric **measurement** | `CN` | 1..k (empty-window cells = `-1`) |
| Named class (readable) | **Apply names** | text **metadata** string | `CN Class` | the name you typed (empty-window cells = `Unassigned`) |
| Named class (numeric) | **Apply names** | numeric **measurement** | `CN Class code` | 1..m (used by the *Color by: CN Class* overlay) |

Giving two CNs the same name merges them. Only `CN Class` and `CN Class code` change; `CN` keeps the original cluster numbers. `CN Class` is stored as text in the cell's metadata, so in *Export → Cell Table...* you must tick it in the column list. It is listed after the numeric measurements.

> **Saving.** In project scope, **Apply names** updates and saves every image from the run, using the **Parallel workers** count. The open image is updated and saved too. In current-image scope it updates only the open image and does not save it; press **Ctrl+S** to save.

**Export:** **Export as PNG…** saves the heatmap. **Export CN frequencies CSV…** saves one row per CN: cell count, fraction of all cells, diversity, and the mean fraction of each cell type. For CN fractions per image, export the cell table (§[12.1](#121-cell-table-export)) with the `CN` or `CN Class` column and count the cells in each image. Use these to check whether CN frequencies follow your biological groups or your staining batches.

### 18.6 Parallel workers (project scope) — performance

**Parallel workers** (default: number of CPU cores minus 1, up to 8) sets how many images are processed at once, in both the sample and the assign pass. Each worker loads one whole image's cells, so memory use rises with the worker count.

- For images with hundreds of thousands of cells, use 2–4 workers.
- For many small images, use more workers.
- Results are the same for any worker count.

### 18.7 Viewer overlays

Three buttons recolour the viewer using the last run. They do not change the cells. Cells with `CN = -1` keep their classification colour.

- **Color by: Neighborhood (CN)**: one colour per CN. CNs that touch in the tissue get contrasting colours.
- **Color by: CN Class**: one colour per named class, after **Apply names** (uses `CN Class code`).
- **Color by: diversity**: colours each cell by the Shannon diversity of the cell types in its window (0 = one cell type, 1 = an even mix). Use it to find mixed zones and interfaces.

Click a button again to return to classification colours. Closing the dialog also returns the viewer to classification colours.

![CN overlay in the viewer alongside the enrichment heatmap and name/merge panel](doc_images/cn_cluster_visualisation.png)

*The **Color by: Neighborhood (CN)** overlay, with the enrichment heatmap and the **Name / merge neighborhoods** panel. Neighbouring CNs get contrasting colours.*

### 18.8 Tips & cautions

- **Check that differences in CN frequency are biological.** Differences between samples are usually the result you are looking for. But CNs are built from your cell classifications, so any staining or batch effect in classification carries into the CN frequencies. Compare per-image CN fractions between your groups and between your batches.
- **Check CNs that come mostly from one or two images.** Such a CN may reflect one unusual slide rather than shared biology. CNs with less than 2% of cells are the least stable; confirm that they appear again (for example in another cohort or a repeat run) before you interpret them.
- **Run the classifier first.** CN results depend on the quality of the cell classifications.
- **within radius** vs **k nearest neighbours**: with **within radius**, dense regions have more cells per window. With **k nearest neighbours**, every window has the same number of cells, so density does not change window size. Choose the mode that suits your question.

---

## 19. Batch normalisation (UniFORM)

Staining intensity often differs between slides or runs. Batch normalisation multiplies each image's intensity for each marker by one correction factor, so that the distribution lines up with a reference. Only the position of the distribution changes, not its shape. Clustering and the classifier then see the same intensity scale across the cohort. The method is feature-level UniFORM (Wang et al., *Cell Reports Methods* 2025; see [README ▸ References](README.md#references)).

Open it from *Extensions → SP Classify → Batch Normalisation...*.

> The correction factor for each channel is calculated from its **Cell: Mean** intensities (or **Cell: Median** if Mean is not selected) and applied to every selected measurement of that channel. Only intensity measurements are corrected; foundation-model embeddings are excluded. Existing measurements are never changed; corrected values are added as new columns only if you click **Write corrected columns** (§19.4).

### 19.1 When to use it

- You cluster or train across images stained in different runs, and clusters or classes follow the slide rather than the biology.
- Clustering normalisation (§[4.2](#42-clustering-normalisation)) applies the same transform to every image, so it cannot remove differences between slides. Batch normalisation corrects each image separately. You do not need it for a single image.

### 19.2 Fitting — step by step

1. **Correct measurements**: click **Choose measurements…** and pick the marker intensities to align. Embeddings are excluded automatically.
2. **Images**: click **Choose images…** to pick the images. To include images from another project, click **Add project…** (next to **Also include projects:**) and select its `project.qpproj`. Measurement names must match. **Clear** removes added projects.
3. **Batch grouping** (optional): batches are first filled in from the image names. Click **Assign batches…** to edit them in the **Assign Images to Batches** table: double-click a cell, select rows and click **Assign selected → batch…**, or use **Auto-detect from name** or **Load CSV…**. Batches are used by **Per batch** mode and by the QC view.
4. **Granularity**:
   - **Per image (each image → reference)** (default): aligns each image to the reference separately.
   - **Per batch (pool images in a batch)**: pools the images in each batch and aligns the batches. Differences between images in the same batch are kept.
5. Set **Bins** (default 1024, range 64–4096), **Cells/image** (cells sampled per image for the fit, default 50,000, range 1,000–2,000,000) and **Workers** (images processed at once, default: number of CPU cores minus 1, up to 8).
6. Click **Run fit**. It calculates the correction factors and saves them to `<project>/celltune/batch-shifts.json`. The fit is kept between sessions; you can run it again at any time.

### 19.3 QC — did it work?

**Show QC** opens *Batch Normalisation — QC*. For each marker it shows log-intensity density curves (one per batch) and the spread between batches before and after correction (**Before (SD)**, **After (SD)**). Lower SD means the batches are better aligned. Check several markers. After correction, the curves for each batch should overlap and the SD should fall. If a curve that had two peaks now has one, the correction may have removed real differences.

### 19.4 How it's applied

There are two separate options:

- **In memory (recommended):** tick **Use batch-corrected values in clustering + ML (streamed, no columns)**. Clustering, training and classification then use corrected values in memory. Cell measurements are not changed. The setting is also in *Edit → Preferences → SP Classify* as **Use batch-corrected values**. It stays on until you untick it, and does nothing until a fit exists.
- **Write corrected columns:** adds a `<marker>: Cell: Mean (batchnorm)` column for each corrected marker, for export or inspection. The raw columns are kept.

### 19.5 Tips & cautions

- **Fit before you cluster or train.** The in-memory option does nothing until the project has a fit.
- **Per batch mode needs a batch grouping.** If all images are in one batch, Per batch mode makes no correction. **Auto-detect from name** sets batches from image names.
- **It can over-correct.** If your groups really differ in intensity, for example treated and control, putting them in different batches removes that difference. Check the QC view for each marker.
- **Projects fitted together need the same marker panel**: measurement names must match in every project.

---

## 20. Tips, tricks and known limitations

- **Label at least 20–30 cells per class** before the first Train. Then use Review Mode to add labels where the two models disagree.
- **Select several cells at once:** hold Ctrl (Windows/Linux) or Cmd (Mac) while clicking cells, then apply a label.
- **Channel viewer:** *View → Channel viewer* shows the area under the cursor in every selected channel side by side.
- **Run Resolve Hierarchy before exporting.** Without it, the `ParentAnnotations` column can be empty for some cells in exports; `ContainingAnnotations` still lists them. Run *Utility Scripts → Resolve Hierarchy...* first (§[13.2](#132-resolve-hierarchy)).
- **Training Metrics overestimate accuracy on new images.** The 20% held-out cells come from the same images used for training. Check predictions on a few images with no labels before relying on the scores.
- **Use different model types for Model 1 and Model 2.** Two models of the same type rarely disagree, so Review Mode finds few cells to review.
- **Images at once is limited to 8.** Each image needs about 2–4 GB of memory on COMET data. This setting is separate from **CPU threads** — see §5.3.
- **If training is slow, check the log.** Each run's log (`<project>/celltune/logs/training-<timestamp>.log`, last 20 kept) ends with a "Where the time went" table showing how long each step took.
- **Channel mapping is saved per project** in `celltune/marker-table.json` and reloads when you reopen the project. To use it in another project, click **Export CSV…** in *Channel Mapping (Review Display)...* and import that CSV there. *Reset Project State* deletes it with the rest of `celltune/`.
- **Fixing a CSV that picks the wrong channels.** Open *Channel Mapping (Review Display)...*, click **Import CSV…**, find rows marked `~`, `!` or `✗`, correct the ticks, click **Pin all matches**, then **Save**. Details in [§4.4](#44-channel-mapping-for-review-marker-table).
- **Project Prediction Summary needs at least 5 images** for reliable robust z-scores. With 2–3 images, use the Anomaly column only as a rough guide.
- **Composite class colours.** Without **Prepend current primary classification (colour follows primary)**, QuPath creates a colour for each composite name, which can mean hundreds of colours. Tick it to keep your existing class colours.
- **Opening an image from Project Prediction Summary does not save the current image.** Save it yourself (Ctrl+S) before you switch if you have edited it.

---

*Found a missing step or a wrong label? Open an issue on the GitHub repo. Screenshots welcome.*
