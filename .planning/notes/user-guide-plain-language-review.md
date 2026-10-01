# USER_GUIDE.md — plain-language review
Review of `USER_GUIDE.md` (branch `feat/channel-mapping-editor`, 1,723 lines) for figurative, verbose, vague and developer-oriented language. Read-only: no edits made yet. Line numbers refer to the file at this review. Facts marked with a `file:line` were checked against the source.
Priority: **High** = vague/unclear where users act or could be misled (incl. factual errors); **Medium** = substantial verbosity / figurative language; **Low** = minor style.

---

## Part A — lines 1-396: intro, TOC, §1 Install, §2-3 Quick starts, §4 Setup steps (4.1-4.4)

### Overall patterns (lines 1-396)
- The text keeps explaining why the code works, not what the user should do. Examples are tree-model invariance, SHAP dilution, the scanpy recipe, `PathClass` and schema versions. The longest cases are §4.1 auto-prune and §4.2, which can be cut by about two-thirds.
- Changelog wording is mixed into the instructions: "the old 5-marker cap is gone", "unchanged", "more forgiving than before", "previously broke", "Earlier versions matched…", "now schema version 2". Users need the current behaviour only.
- Thresholds are given as approximations ("~5 cells", "~0.95", "~50 markers", "takes milliseconds", "can take a while") when the source has exact values.
- There is one factual error. The guide says auto-prune runs on the normalised matrix, but the code prunes raw values.
- Names are inconsistent: "marker table", "channel mapping" and "Channel Mapping editor" all mean one thing. Menu paths use both `→` and `▸`. Some labels differ from the UI ("Set Images at once", "Enable").
- Figurative words to remove: "surface", "squash", "tames", "dynamic-range compressor", "goes blind", "light up", "land on", "uncover it", "Great for".

### Suggestions

**1. Lines 3 — FIGURATIVE / DEVELOPER DETAIL — Medium**
- Current: "uses their **disagreement** to surface the cells that need your attention. Everything runs in-process."
- Problem: "surface" is figurative, and "in-process" is developer wording.
- Suggested: "A cell classifier for QuPath 0.7 that learns from cells you label. It trains two models on the same labels (XGBoost and LightGBM by default) and lists the cells where the two models predict different classes, so you can check and correct those cells first. It runs inside QuPath and does not need Python."

**2. Lines 5-7 — VERBOSE / VAGUE — Medium**
- Current: "Please note that function speed is dependent on hardware … **please be patient**. Windows and boxes can be expanded or contracted by clicking and dragging corners which will display buttons correctly…"
- Problem: "Please note" is filler, the second paragraph is hard to follow, and neither says what the user should do.
- Suggested: "On large projects or images, windows and tasks can take several seconds to open or start. Wait before clicking again. If a window opens with buttons cut off, drag its corner to make it larger (the cursor changes to a resize arrow over the corner)."

**3. Line 14 — UNCLEAR INSTRUCTIONS — Low**
- Current: "Add your own screenshots next to each section heading after install."
- Problem: This is a note to the document author, not an instruction for the reader.
- Suggested: delete the line.

**4. Lines 77-78 — VAGUE — High**
- Current: "Drop the JAR into QuPath's `extensions/` folder, or drag-and-drop it onto the running QuPath window."
- Problem: The folder location is not given. The guide also does not warn that an older JAR left in the folder can be loaded instead of the new one (CLAUDE.md "Install in QuPath").
- Suggested: "2. Delete any older `qupath-extension-sp-classify-*-all.jar` from QuPath's extensions folder. If an old copy stays there, QuPath may load it instead of the new one. Then copy the new JAR into the folder, or drag it onto the QuPath window:
   - Windows: `C:\Users\<you>\QuPath\v0.7\extensions\`
   - Linux: `~/.local/share/QuPath/v0.7/extensions\`
   - macOS: `~/Library/Application Support/QuPath/v0.7/extensions/`"
  (Paths are from CLAUDE.md "Install in QuPath". Use `/` at the end of the Linux path.)

**5. Line 79 — UNCLEAR INSTRUCTIONS / FIGURATIVE — High**
- Current: "The **SP Classify** panel docks into the analysis tab pane on the right, you can mouse over the area and scroll the mouse wheel to uncover it."
- Problem: This is a comma splice, "uncover" is figurative, and it is not clear what the user scrolls.
- Suggested: "3. Restart QuPath. **SP Classify** is added as a tab in the analysis pane (the panel with the Project, Image and Annotations tabs). If the tab is not visible, hold the mouse over the row of tab names and scroll the mouse wheel until **SP Classify** appears."

**6. Line 82 — VAGUE — Low**
- Current: "**Edit → Preferences → SP Classify → Enable**"
- Problem: The label is wrong.
- Suggested: "To turn the extension off, go to **Edit → Preferences → SP Classify** and untick **Enable SP Classify extension**." (`strings.properties:6`, `SpClassifyExtension.java:716-718`)

**7. Lines 99-105 — UNCLEAR INSTRUCTIONS / VAGUE — High**
- Current: "label ~20–50 cells across the open image … Set Images at once, pick settings (Pool labels, Balancing, Early stopping, etc.) … Train → inspect…"
- Problem: "~20–50 cells" here conflicts with §5.1, which says "at least 20–30 per class". "Set Images at once" reads as an instruction but is a spinner label. "etc." gives no specifics.
- Suggested flowchart lines: "Manual Label Mode → label at least 20–30 cells per class (training needs at least 10 labelled cells in total)" (`ClassificationPanel.java:672-675`) and "Set training options: Pool labels from all images, Enable data balancing, Early stopping, Images at once (number of images predicted in parallel; default 1, max 8)" (`ClassificationPanel.java:92-93,102,175,210`).

**8. Lines 127, 137-138 — UNCLEAR INSTRUCTIONS — Low**
- Current: "Binary Classifiers... → Create "CD3" → Open" / "tick "Prepend current primary classification" to keep multiclass colouring"
- Problem: Button and checkbox labels are not exact, and "keep multiclass colouring" is vague.
- Suggested: "Extensions → SP Classify → Binary Classifiers... → **Create...** (name it CD3) → **Open** (enters Binary Mode)" (`BinaryClassifierPanel.java:59-60`). For the checkbox: "(optional) tick **Prepend current primary classification (colour follows primary)** so each cell keeps its multi-class class name and colour in front of the marker labels" (`CompositeClassificationDialog.java:44`).

**9. Lines 159, 164-169 — VERBOSE — Low**
- Current: "Great for removing large groups of features." / "so nothing is silently misfiled into Morphology."
- Problem: The first is marketing tone and the second is a developer justification.
- Suggested: "**Select All** / **Clear All**: tick or untick every feature currently shown by the search filter." Cut ", so nothing is silently misfiled into Morphology" and write "**Other / Uncategorized**: features that fit none of the groups above."

**10. Line 173 — DEVELOPER DETAIL / VERBOSE — Medium**
- Current: "Both default models are gradient-boosted trees, which are robust to correlated and redundant features: at each split a tree picks … muddying SHAP plots…"
- Problem: About 90 words on how tree models work. The user only needs to know whether to prune by hand.
- Suggested: "**Do you need to remove features by hand?** Usually not. Extra or near-duplicate features rarely lower accuracy with the default models, but they make training slower and split a marker's importance across several columns in the feature-importance plot. Leave **Auto-prune features** ticked to remove them automatically."

**11. Line 175 — VAGUE / factual error — High**
- Current: "Pruning runs on the **pooled, normalised training matrix** … after normalisation…"
- Problem: Wrong. Pruning runs on raw values, because the classifier never uses normalised values (`ClassificationPanel.java:940-944, 977`). The paragraph is also long, and it leaves out that pruning is skipped when there are fewer than 10 rows (`ClassificationPanel.java:996`). The checkbox tooltip has the same "normalised" error (`ClassificationPanel.java:295`).
- Suggested: "**Auto-prune features (drop near-constant & redundant)** is ticked by default (`ClassificationPanel.java:104,294`). At the start of each training run it removes features from that run's feature list. It does not delete measurements. The decision uses raw values from your labelled cells on the open image plus labelled cells pooled from other images. Rows imported from CSV are trained on but not used for the decision. Pruning is skipped if there are fewer than 10 labelled cells. It works in four steps:"

**12. Lines 177-180 — VAGUE / FIGURATIVE — High**
- Current: "fewer than ~5 cells … exceeds ~0.95 … So the classifier never goes blind to a marker…"
- Problem: The values are exact in the code, and "goes blind" is figurative.
- Suggested:
  "1. **Constant features**: removes a feature that is non-zero in fewer than 5 cells or has zero variance.
  2. **Duplicates within a marker**: within each marker group, keeps the feature with the highest variance and removes any feature whose absolute Pearson correlation with a kept feature is above 0.95. For example, `CD3: Cell: Mean`, `CD3: Cell: Median` and `CD3: Cell: Max` become one column.
  3. **Duplicates across markers**: off by default, so two different markers are never merged.
  4. **Minimum kept per marker**: the 5 highest-variance features in each group are always kept, so every marker keeps at least 5 features (or all of them if it has 5 or fewer)."
  (`FeaturePruner.java:70`, `PruneOptions(5, 0.95, 1.0, 5)`; 1.0 = cross-marker off)

**13. Lines 182-184 — VERBOSE — Medium**
- Current: "This pruning grouping is deliberately *separate* from the feature-picker categories … Pruning takes milliseconds … The net effect is the same 'near-identical accuracy, much faster training…'"
- Problem: Line 184 repeats line 173 and quotes a phrase from nowhere. "Takes milliseconds" is unverified.
- Suggested: "> **Marker groups for pruning:** a feature's group is the text before the first `: ` (`CD3: Cell: Mean` → CD3). If there is no `: `, the group is the text before the first underscore or space (`kronos_emb_0` → kronos). Case is ignored. These groups are not the same as the Morphology / Neighbors / Embeddings groups in the feature picker." Delete line 184.

**14. Line 192 — UNCLEAR INSTRUCTIONS / VERBOSE — High**
- Current: "(tree models are invariant to it anyway). Same prefix/search/select-all UI as Select Features, plus:"
- Problem: The bracketed point repeats line 211. The UI is also described wrongly: this window has a **Search**, a prefix dropdown with **Select Prefix** / **Clear Prefix**, and **Select All** / **Clear All**, not the grouped tree (`ClusteringNormalizationPane.java:105-128`).
- Suggested: "These transforms are used only by clustering, the scatter plot and gating (§11). The classifier always uses raw values. The window has a search box, a prefix dropdown with **Select Prefix** / **Clear Prefix**, **Select All** / **Clear All**, and:"

**15. Lines 195-198 — VERBOSE / VAGUE — High**
- Current: "scale-dependent, so there is no single right number … Quick check: if almost every cell's raw value is *below* the cofactor … eyeball the transformed histogram…"
- Problem: The text is long. It leaves out the **Suggest…** button (`ClusteringNormalizationPane.java:93`) and the cofactor default of 1.0 (`ClusteringNormalizationPane.java:85`), which is too low for raw fluorescence and has to be changed.
- Suggested: "- **arcsinh**: `arcsinh(x / cofactor)`. Recommended. Set **Cofactor:** (default 1.0) for your data, or click **Suggest…** for a suggested value.
    - Raw fluorescence (COMET, CODEX, IF; values in the hundreds to thousands): 25–50.
    - MIBI: 0.05 (Hartmann et al. 2021; see [References](README.md#references)).
    - Choose a value near the boundary between background and positive signal. If almost all raw values are below the cofactor, the transform changes very little. If almost all are far above it, low-intensity differences are lost."

**16. Lines 203-205 — VERBOSE — Low**
- Current: two separate short paragraphs.
- Suggested: "The one transform and cofactor apply to every ticked feature. Unticked features stay raw. Do not tick morphology features (e.g. Cell Area) or features that are already normalised (e.g. foundation-model embeddings)."

**17. Lines 207-212 — FIGURATIVE / DEVELOPER DETAIL / VERBOSE — Medium**
- Current: "a monotone, per-feature squash … Euclidean distance / kNN … XGBoost / LightGBM / Random Forest split on rank order … export no longer writes `__norm` columns."
- Problem: About 250 words, with figurative wording ("squash"), algorithm detail and a changelog note, and it repeats line 192.
- Suggested replacement: "**Why use it.** Clustering compares cells by distance across all markers. Without a transform, a few very bright markers decide most of the result. arcsinh reduces the effect of bright outliers and keeps differences between dim cells.
**What it does not do.** It does not change the classifier, auto-prune, feature importance or ground-truth export, which all use raw values. It does not correct differences in staining or exposure between slides, because the same transform is applied to every image. For that, use batch normalisation (§19) and label cells on several different slides."

**18. Lines 214-224 — DEVELOPER DETAIL / FIGURATIVE / VAGUE — Medium**
- Current: "mirroring the scanpy `scale → PCA → neighbours → Leiden` recipe … *dynamic-range compressor* … tames within-marker skew…"
- Problem: The figurative wording and scanpy detail are not needed. "~50" has an exact value.
- Suggested: "**What clustering does automatically.** The transform in this window is optional. Every clustering run also (1) z-scores each marker (subtracts the mean and divides by the standard deviation) and (2) applies PCA when more than 50 markers are selected and **Reduce dims (PCA)** in the scatter plot is ticked (default: ticked). If you set no transform here, clustering runs on z-scored raw values. arcsinh adds one thing z-scoring cannot: it reduces extreme high values within a marker." (`ScatterMath.java:133,214`; `ScatterPlotView.java:140,388`). Keep the ASCII diagram or drop it.

**19. Lines 237, 242, 246 — VAGUE / DEVELOPER DETAIL — Low**
- Current: "(labels stay on disk, invisible)" / "the audit trail makes the merge fully reversible" / "re-adds the source `PathClass`"
- Problem: "invisible" does not say whether those labels are used in training. `PathClass` is developer wording.
- Suggested: "Leave it unticked to remove the class from the class list only. The saved labels are kept on disk." (Whether those labels are still used in training is unverified; check `ClassManager`.) "…restores each label to its original name and adds the original class back to the class list." For line 242: "Undo it from the **Undo Merge** tab."

**20. Lines 250-254 — UNCLEAR INSTRUCTIONS (terminology) / VERBOSE — Medium**
- Current: "A **marker table** (also called the **channel mapping**) maps each cell type … land on a predicted `CD4T` cell…"
- Problem: Two names for one thing, used throughout §4.4 and the menus ("Import Marker Table…" vs "Channel Mapping (Review Display)…"), plus figurative "land on".
- Suggested: "Optional. The **channel mapping** lists which image channels to show for each class. In Review Mode, the viewer then shows only those channels for the current cell. For example, for a cell predicted as `CD4T` it shows CD4 and CD3. (The CSV file version is called a marker table in the **Import** menu.)" Then use "channel mapping" consistently in the rest of the section.

**21. Lines 307, 324-326, 333-338, 369-372 — DEVELOPER DETAIL (changelog) — Medium**
- Current: "(the old 5-marker cap is gone)" / "The file is now schema **version 2**…" / "is unchanged … Reading is more forgiving than before … (it previously broke rule-format CSVs)" / "Earlier versions matched against QuPath's display names … `CD3` could also light up `CD31`."
- Problem: These are release notes, and "light up" is figurative.
- Suggested: line 307 → "There is no limit on channels per class. Above 8, the editor shows a warning that the combined image may be hard to read." (`strings.properties:153`). Lines 324-326 → "Older versions of SP Classify can open this file but ignore the exact channels you ticked." Lines 333-338 → "**Extensions ▸ SP Classify ▸ Import ▸ Marker Table...** and the editor's **Import CSV…** accept both formats below. Fields in quotes may contain commas, the byte-order mark Excel adds is ignored, and extra columns are ignored." Lines 369-372 → "Names are matched against the image's own channel names, not QuPath's display names with ` (C4)` added. Names typed with the `(C4)` suffix still work." Move the version history to CHANGELOG.

**22. Lines 359-377 — VERBOSE — Medium**
- Current: "> **Matching is tolerant — but the `CellType` column should track your class names** …" (five paragraphs)
- Problem: The callout is long, and one point is buried after the changelog text.
- Suggested: "> - `CellType` must match your class names. Case, spaces and punctuation are ignored (`CD4 T` = `cd4t` = `CD4-T`).
> - Marker names are matched to channel names the same way. `CD3` matches a channel named `CD3 (Opal 570)`.
> - Channels you ticked in the editor are matched by exact name first. If that name is not in the image, the matching above is used.
> - If a class is not in the table, or none of its markers match a channel, the viewer channels are not changed."

**23. Lines 302-305 — VAGUE — Low**
- Current: "Opening each image's server can take a while on big projects."
- Problem: "Server" is developer wording, and "a while" is vague.
- Suggested: "**Scan project channels** opens every image in the project to read its channel names. On large projects this can take several minutes (time not measured); you can cancel it." Mark the duration unverified.

**24. Lines 153 vs 333 — UNCLEAR INSTRUCTIONS (consistency) — Low**
- Current: "*Extensions → SP Classify → Select Features...*" vs "**Extensions ▸ SP Classify ▸ Import ▸ Marker Table...**"
- Problem: Two different separators for menu paths.
- Suggested: use `→` everywhere, matching the guide's other menu paths.

Total: 24 (8 high, 9 medium, 7 low)

---

## Part B — lines 397-812: §5 Multi-class workflow, §6 Binary + composite, §7 Review mode, §8 Project Prediction Summary, §9 Intensity heatmaps, §10 Distance measurements

### Overall patterns (lines 397-812)
- **Wrong facts that users act on.** Checking against the source, the guide gets several things wrong: the review batch size (the default is 200, not 256), the format of composite class names, the label on the **Avg** button, which models feed Feature Importance, and what **Open Selected Image** does with unsaved work.
- **The same point is made twice.** Train/val metrics (table row and a paragraph), Images-at-once and worker advice (§10 says it twice with different numbers), and Rounds/Early stopping are each explained more than once.
- **Figurative and chatty phrasing.** Examples: "the F1 lied", "the honest test", "the classifier doesn't understand this slide", "hotspots", "backbone", "AND gate", "blowing up", "masquerading", "nudge", "the whole point", "cheaper than running out of memory twenty minutes in".
- **Developer detail in a user guide.** Examples: the "Backed by `DistanceTools.…`" column, the TreeSHAP/monotone-transform notes, the robust-z constant 0.6745 and its justification, "in-memory", `PathClass`, "saved XGBoost model", "lightweight overlay".
- **Vague limits where the code has exact values.** Ranges and defaults for Rounds, Max depth, Images at once, Parallel image workers and Auto-tune trials/folds, and size words like "big panel", "hours" and "tiny projects".
- **Inconsistent names for the same control.** The prediction buttons are called "Model 1/Model 2", "M1/M2" and "XGB/LGB". The **Prepend…** checkbox is later called "the merge mode".

### Suggestions

**1. Lines 405-408 — VERBOSE/DEVELOPER — Low**
- Current: "A **magenta ring** marks the selected cell (a lightweight overlay — won't slow down 50k+ cell images)."
- Problem: The bracketed performance note does not affect anything the user does, and the status dot colours are buried in a long bullet.
- Suggested: "- Click a cell in the viewer. The toolbar shows its ID and current class. The status dot is lime if the cell is labelled and white if it is not.
- The selected cell has a magenta ring.
- Up to 12 class buttons are shown in the toolbar (ManualLabelToolbar.java:367-370). Other classes are under **All Classes ▼**.
- **Auto-advance to next detection**: when ticked, the next cell is selected after you assign a label."

**2. Line 410 — VERBOSE — Low**
- Current: "You can — and should — add more after each review cycle."
- Problem: The dashes add emphasis but no information.
- Suggested: "**How many cells to label:** label at least 20–30 cells per class before the first training run. Training will not start with fewer than 10 labelled cells in total (ClassificationPanel.java:672-675). Add more labels after each review cycle."

**3. Line 412 — UNCLEAR — Medium**
- Current: "The **Model 1** / **Model 2** buttons … Background colour: blue = M1, pink = M2."
- Problem: The buttons are labelled `Model 1: <class> (NN%)` here, but in Review mode the same buttons say `XGB:`/`LGB:`, and the guide does not connect the two. It also adds a third name, "M1/M2".
- Suggested: "After the first training run, two extra buttons appear: **Model 1: <class> (NN%)** (blue) and **Model 2: <class> (NN%)** (pink) (ManualLabelToolbar.java:300,306). Click one to accept that model's prediction for the selected cell."

**4. Lines 420-425 — UNCLEAR/VERBOSE — Medium**
- Current: "Dual-list selector. Left = images … This is a quick way to reduce prediction times but only focusing on one or a few images."
- Problem: "Dual-list selector" is jargon, and the last sentence does not parse.
- Suggested: "The left list holds the images the classifier will be applied to; the right list holds images that are excluded. Use the search box and arrow buttons to move images between the lists. The open image is always included. Click **OK**; the button then shows the count, e.g. `Apply to which images... (12)`. Choose fewer images to make the apply step faster."

**5. Lines 431-436 — FIGURATIVE/VERBOSE — Medium**
- Current: "Leave at **0**, which means "use all CPU resources. … One thing to know: changing this number can nudge the results very slightly."
- Problem: The quote mark is never closed, "nudge" is figurative, and the paragraph is longer than it needs to be.
- Suggested: "**CPU threads** sets how many processor threads training uses. The default is **0**, which uses all processors (ClassificationPanel.java:186-196). Lower it to keep QuPath responsive during training or when sharing a compute node. The value is remembered between sessions. Changing it can change results very slightly, so use the same value when comparing two training runs."

**6. Lines 438-439 — VAGUE — Medium**
- Current: "**1** on a 16 GB machine, **2–3** on 32 GB. Maximum 8. If you set it too high, QuPath may run out of memory."
- Problem: The default and minimum are not stated, and the memory guidance has no source.
- Suggested: "**Images at once** sets how many images are classified at the same time after training. The default is 1 and the range is 1–8 (ClassificationPanel.java:175). It has no effect on training itself. Each image is fully loaded into memory, so raising this uses more memory. Suggested values: 1 on a 16 GB machine, 2–3 on 32 GB (unverified guidance). If QuPath runs out of memory, set it back to 1."

**7. Lines 443-455 — VAGUE/FIGURATIVE — Medium**
- Current: "The defaults are tuned for typical multiplex panels." / "(always — no downside)" / "drilling into one tricky FOV" / "one class << others"
- Problem: "Typical" and "no downside" are unsupported claims, and "drilling into" and `<<` are informal shorthand.
- Suggested: Replace the intro with "Most users can leave these at their defaults." Replace these cells: Early stopping ON → "recommended"; Data balancing ON → "one class has many fewer labels than others"; Sample current image only ON → "you want to review cells from the open image only"; Show top 10 → "recommended"; Auto-tune ON → "you can leave training running for several hours (see Auto-tune below)".

**8. Line 459 — VERBOSE — Medium**
- Current: "**Leave as default if you don't understand this** This is complicated and involves generating synthetic data or removing datapoints…"
- Problem: The bold sentence has no full stop, and the explanation that follows is a long sentence that tells the user nothing useful.
- Suggested: "Leave this at the default (`SMOTE + Tomek`) unless you have a specific reason to change it. These methods add synthetic cells to small classes, remove cells near class boundaries, or both."

**9. Line 470 — VAGUE/FIGURATIVE — Medium**
- Current: "Defaults work for ~90% of cases. … if a minority class lives in a hard region of feature space."
- Problem: "~90%" has no source, and "lives in a hard region" is figurative.
- Suggested: "Use `SMOTE` alone if Tomek removes too many labelled cells from a class (the training log shows counts before and after balancing). Use `ADASYN` if a small class is often confused with a larger one."

**10. Line 472 — VERBOSE (duplicate) — Medium**
- Current: "**Train/val metrics.** On by default. This is what fills in the **Training Metrics** report…"
- Problem: This repeats the table row at line 451.
- Suggested: "**Train/val metrics** (on by default) produces the Training Metrics report. It adds about a third to training time. The trained classifier is the same with it on or off. Untick it while you are still adding labels, and tick it again for your final run."

**11. Line 474 — VAGUE — High**
- Current: "It trains 200 models … On a big panel that's hours, not minutes — plan for overnight."
- Problem: The guide does not say where 200 comes from or that the panel has Trials and Folds controls that change it.
- Suggested: "**Auto-tune** tests different model settings. The number of models it trains is 2 × Trials × Folds. The defaults are 20 trials and 5 folds, so 200 models (HyperparameterTuner.java:34,37,235). Trials can be set from 5 to 100 and Folds from 2 to 10 (ClassificationPanel.java:254,262). With many features and classes this can take several hours. The training log shows the number of models before it starts."

**12. Lines 476-478 — FIGURATIVE/DEVELOPER — Medium**
- Current: "that's the whole point of dual-model disagreement" / "a graphics card only starts to pay off…"
- Problem: Both sentences are conversational, and the GPU paragraph gives the user nothing to act on.
- Suggested: "**Models 1 & 2.** The default is XGBoost (Model 1) and LightGBM (Model 2); Random Forest is also available. Use two different model types: review mode relies on the two models disagreeing. Auto-tune runs separately for each model. Training runs on the CPU only." (Delete the remainder of line 478.)

**13. Lines 480-486 — VERBOSE — Medium**
- Current: "So a model that only needs 130 rounds uses 130 and the setting costs you nothing; … That's why the default is generous."
- Problem: Seven lines say one thing, and the ranges are missing.
- Suggested: "**Rounds / Max depth.** Defaults are 500 rounds (range 50–1000) and depth 6 (range 2–15) (ClassificationPanel.java:157,162). With **Early stopping** on (the default), Rounds is a maximum: each model stops when it stops improving. With Early stopping off, every round is trained, so lower Rounds to reduce training time. The training log shows the rounds used, e.g. `best round 127/500`. If the number is close to the maximum, raise Rounds."

**14. Lines 490-492 — FIGURATIVE/VERBOSE — Medium**
- Current: "cancelling now is cheaper than running out of memory twenty minutes in. It's a rough check…"
- Problem: The sentence is conversational and does not tell the user what to do after a warning.
- Suggested: "Click **Train**. A progress window shows the current step. Before training, SP Classify saves a backup of the labels to `<project>/celltune/labels_backup_*.json` and estimates the memory needed. If memory may be too low, a warning offers **Proceed** or **Cancel**. The estimate is approximate. If you cancel, reduce the number of features, or close other images or programs, and try again."

**15. Lines 498-512 — VERBOSE — Low**
- Current: "The on-screen log disappears when you close the progress window; this one doesn't, and it survives a crash. … so you can send it to someone without having to explain the setup."
- Problem: The section can be about half as long.
- Suggested: "Each training run saves its log to `<project>/celltune/logs/` (the 20 most recent are kept; TrainingLogRecorder.java:43). The file is kept after the progress window closes and after a crash. Nothing is saved when no project is open. The log lists the cell and label counts, all settings and the available memory. It ends with the time taken by each step, slowest first: [example]. If training is slow, this table shows which step took longest. If a run fails, the last line names the step that failed."

**16. Lines 516-527 — UNCLEAR — High**
- Current: "Two views are unlocked after a successful run" / "rows = XGBoost prediction, columns = LightGBM prediction" / "per-class recall-style %"
- Problem: Three views follow, not two. The axes are Model 1 and Model 2, which are not always XGBoost and LightGBM. "Recall-style" is not defined.
- Suggested: "Three buttons become available after training: **Confusion Matrix**, **Training Metrics** and **Feature Importance**. … Rows are Model 1's predictions and columns are Model 2's (axis titles `Model 1 (XGBoost)` / `Model 2 (LightGBM)`; ConfusionMatrixView.java:88-89). Right column: for each Model 1 class, the percentage of its cells that Model 2 also assigned to that class. Bottom row: the same for each Model 2 class."

**17. Line 529 — FIGURATIVE — Medium**
- Current: "large off-diagonal hotspots show systematic confusion pairs (e.g. CD4/CD8 cross-talk)"
- Problem: "Hotspots" and "cross-talk" are figurative.
- Suggested: "If most cells are on the diagonal, the two models mostly agree. A large off-diagonal value shows two classes the models often confuse (e.g. CD4 and CD8). Label more cells of those two classes in the next round."

**18. Line 551 — UNCLEAR — Low**
- Current: "with both absolute counts and row-normalised recall heatmaps, plus a per-row diagonal = recall."
- Problem: The end of the sentence does not parse.
- Suggested: "**Validation Confusion Matrix** compares the true class (rows) with the predicted class (columns) for the same 20% validation cells. It shows two heatmaps: cell counts, and counts as a percentage of each row. The diagonal value in the percentage heatmap is the recall for that class."

**19. Line 559 — FIGURATIVE — Medium**
- Current: "The honest test is … If a slide has predictions that look wrong by eye, the F1 lied — go label some of its cells."
- Problem: The F1 score is personified, and the tone is conversational.
- Suggested: "**A high F1 score does not mean the classifier works on new images.** The validation cells come from the same images as the training cells, so F1 overestimates performance on other images. To check, open a different image, apply the classifier, look at the results, and check the Project Prediction Summary (§8). If predictions on an image look wrong, label some of its cells and retrain."

**20. Lines 563-565 — DEVELOPER/incorrect — High**
- Current: "SHAP is averaged across whichever models are active (TreeSHAP for XGBoost/LightGBM, normalised split counts for Random Forest)."
- Problem: LightGBM is never included. With the default XGBoost + LightGBM pair, the chart shows XGBoost only (DualModelClassifier.java:976-1003, which uses at most 5,000 sampled cells). The cofactor note in the bracket is developer detail.
- Suggested: "Shows the 10 features that most affect each class's prediction. Use the dropdown to switch class. Values come from the XGBoost model and from any Random Forest model; LightGBM is not included. If one feature (e.g. `Cell: DAPI Mean`) ranks highest for every class, or a non-biological column such as a cell ID or centroid coordinate ranks near the top, de-select it in Select Features (§4.1) and retrain."

**21. Lines 573-580 — UNCLEAR — High**
- Current: "Two controls above the buttons: … Leave both blank to sample across every cell…"
- Problem: "Above the buttons" does not say which buttons. A checkbox cannot be "blank". The screenshot shows a "Specify annotations" dialog that the text never mentions.
- Suggested: "Two controls above **Enter Review Mode** limit which cells review mode samples:
- **Sample current image only** (checkbox): sample from the open image only.
- **Annotation keyword field** (placeholder text `Filter by annotation keywords (comma-separated, e.g. Tumour)`; ClassificationPanel.java:361): only cells whose centre is inside an annotation whose name contains one of the words (not case-sensitive). Example: `Tumour, Margin`.
Leave the box unticked and the field empty to sample from every cell in every image (default)." Also check whether the "Specify annotations" dialog (ClassificationPanel.java:1915) needs its own step.

**22. Line 586 — VERBOSE/marketing — Low**
- Current: "Great for smaller panels or functional markers like Ki67."
- Problem: "Great for" is marketing tone.
- Suggested: "Use this for panels with few markers, or for markers that describe cell state, such as Ki67."

**23. Lines 592-593 — VAGUE/DEVELOPER — Medium**
- Current: "Marker names are sanitised to safe filesystem characters. … a state file `<project>/celltune/binary/CD3.json` is created when you first train."
- Problem: "Sanitised" is vague, and the file paths are internal detail.
- Suggested: "Click **Create...** and enter a marker name (e.g. `CD3`). Characters other than letters, digits, `.`, `_` and `-` are replaced with `_` (BinaryClassifierRegistry.java:53)." Move the file-path sentence to CLAUDE.md.

**24. Line 602 — VAGUE — Low**
- Current: "Train, review, and iterate until you're happy."
- Problem: "Until you're happy" gives no stopping point.
- Suggested: "Repeat train → review → retrain until the Training Metrics F1 stops improving and the predictions look correct on images you did not label. Then click **Exit Binary Mode**."

**25. Lines 610-611 — DEVELOPER/VAGUE — Low**
- Current: "Only markers that have been trained (have a saved XGBoost model) appear."
- Problem: The bracket is internal detail, and the defaults are not stated.
- Suggested: "**Markers**: one checkbox per trained binary classifier, all ticked by default. **Images**: one checkbox per project image, all ticked by default (CompositeClassificationDialog.java:91,126)."

**26. Lines 616-621 — DEVELOPER/incorrect — High**
- Current: "classified **in-memory** … composite `PathClass` named by joining the marker results alphabetically: `CD3+:CD8-:CD45+`"
- Problem: Marker names are sorted as text, so the order is `CD3, CD45, CD8` and the example is wrong (CompositeClassifier.java:108-109). "In-memory" and `PathClass` are developer terms.
- Suggested: "Click **Apply**. The open image updates in the viewer immediately. Other selected images are opened, classified and saved; progress is shown in the dialog's log. Each cell gets a class made of the marker names in text sort order, each followed by `+` or `-`, e.g. `CD3+:CD45+:CD8-`. A marker is `+` when its positive probability is 0.5 or higher (CompositeClassifier.java:152)."

**27. Lines 623-626 — FIGURATIVE/inconsistent — Medium**
- Current: "captured **before** any reassignment and prepended … Use the merge mode … the cell-type "backbone""
- Problem: "Merge mode" is a different name for the **Prepend…** checkbox, and "backbone" is figurative.
- Suggested: "**Prepend current primary classification** (off by default): when ticked, each cell's existing class is added to the front, e.g. `Tumour:CD3+:CD8-`, and the cell keeps the colour of its existing class. Cells with no class get the marker-only name. Use this after running a multi-class classifier, so each cell keeps its cell type and gains the marker results."

**28. Lines 634-644 — VAGUE/incorrect — High**
- Current: "Default budget @ 256 cells" / "calibrated for the default 256-cell batch"
- Problem: The guide never mentions the dialog that asks how many cells to review, and the default in that dialog is **200**, not 256 (ClassificationPanel.java:1367-1370; SpClassifyExtension.java:1432-1435). Tiers 0 and 3 only run under conditions the guide leaves out (UncertaintySampler.java:156,160-161).
- Suggested: "Click **Enter Review Mode**. A dialog asks how many disagreement cells to review (default 200; the number available is shown). Cells are picked in five steps. A cell picked in one step is not picked again:

| Step | Picks | Budget per 256 cells |
|---|---|---|
| 0 — Images/FOVs | cells from images with the highest disagreement rate (only when FOV information exists) | 84 total, up to 14 per FOV |
| 1 — Confused classes | cells from the classes with most disagreement | 112 total, up to 16 per class |
| 2 — Rare classes | cells from small classes | 60 total, up to 10 per class |
| 3 — Chosen class pairs | pairs you specify, e.g. `CD4:CD8` (skipped if none are set) | 40 total, up to 8 per pair |
| 4 — Random | the remaining budget | — |

Budgets scale with the number you enter (×N/256, minimum 1) (UncertaintySampler.java:37-44,155)." Also say where Step 3 pairs are entered (unverified, not found in this range).

**29. Lines 656-659 — UNCLEAR/incorrect — Medium**
- Current: "**XGB: ClassName (89%)** — accept Model 1's… **Avg: ClassName (XX%)**"
- Problem: The labels are fixed as `XGB:`/`LGB:` even when Random Forest is selected. The Avg button shows no percentage (ReviewToolbar.java:239,245,254).
- Suggested: "- **XGB: <class> (NN%)** (blue): accept Model 1's prediction. **LGB: <class> (NN%)** (pink): accept Model 2's prediction. These labels stay the same even if you chose Random Forest.
- **Both: <class> (NN%)**: shown instead when the two models agree.
- **Avg: <class>** (green, no percentage): shown only when averaging the two models gives a class that neither model chose."

**30. Line 663 — VERBOSE — Low**
- Current: "…in bold dark blue (e.g. `◆ Tumour, Stroma`), so you keep the spatial context without leaving review."
- Problem: The closing clause adds nothing.
- Suggested: "The toolbar header also shows the names of any annotations containing the current cell, e.g. `◆ Tumour, Stroma`."

**31. Line 686 — UNCLEAR — Medium**
- Current: "Cohort-level QC across every image … See [HOW_IT_WORKS_PREDICTION_SUMMARY](#anatomy-of-the-anomaly-score) below for the maths."
- Problem: The link text looks like a file name, and "cohort-level QC" is jargon.
- Suggested: "Shows one row per project image, using the saved predictions in `<project>/celltune/image-predictions/`, and gives each image an anomaly score so you can find images to check first. See [How the anomaly score is calculated](#anatomy-of-the-anomaly-score)."

**32. Line 698 — UNCLEAR (data loss) — High**
- Current: "**Open Selected Image** — jumps QuPath to that image without saving the current one (deliberately fast for navigation)."
- Problem: The code turns off QuPath's "save changes?" prompt, so unsaved changes in the open image are lost with no warning (ProjectPredictionSummaryView.java:327-341). The current text does not make this clear.
- Suggested: "**Open Selected Image**: opens the selected image. **Unsaved changes in the current image are discarded without a prompt.** Save first (*File → Save*) if you have made changes."

**33. Lines 705-723 — DEVELOPER/FIGURATIVE/VERBOSE — Medium**
- Current: "AND gate … blowing up the composition distance … not two independent dials … `0.6745` is the constant that makes MAD a consistent estimator…"
- Problem: About 20 lines of statistics with figurative wording; the user only needs the formula and the flag rules.
- Suggested: Replace lines 707-723 with: "For each image: (1) **Composition distance**: how different the image's class proportions are from the whole project (Jensen-Shannon distance). (2) **Disagreement rate**: disagreements ÷ predicted cells. Both are converted to robust z-scores (based on median and MAD) across all images. **Anomaly score = 0.65 × composition z + 0.35 × disagreement z** (negative z-scores count as 0). Composition gets the larger weight because the disagreement rate partly depends on which two model types you chose. Use the score to rank images; it is not a probability.
**Flags:** `RARE_ENRICHMENT`: a class under 1% of all cells in the project has at least 20 cells in this image and is at least 3× more common here. `COMPOSITION_OUTLIER`: composition z ≥ 3. `HIGH_DISAGREEMENT`: disagreement z ≥ 3." Move the "Why these numbers?" text to CLAUDE.md.

**34. Lines 727-731 — FIGURATIVE/VAGUE — Medium**
- Current: "the classifier doesn't understand this slide … a per-slide artefact masquerading as it … tiny projects (< ~5 images)"
- Problem: The classifier is personified, "masquerading" is figurative, and "< ~5" is imprecise.
- Suggested: "- Flagged with high disagreement: the classifier performs poorly on this image. Open it, label 10–20 cells, and retrain.
- Composition outlier with low disagreement: either real biological difference, or a staining or segmentation problem. Check the image visually.
- Rare enrichment: check whether the cells really are that class or are a staining or segmentation artefact.
> With fewer than 5 images, z-scores are unreliable."

**35. Lines 739-741 — VERBOSE — Medium**
- Current: "the standard "mean marker expression per phenotype" view used to sanity-check … Rows are cell classes (the `PathClass` assigned to each detection)…"
- Problem: Two paragraphs say the same thing.
- Suggested: "Shows the mean whole-cell intensity of each marker for each cell class. Rows are classes and columns are markers (`<marker>: Cell: Mean` measurements). Use it to check that each class has high values for its expected markers, e.g. CD8 T cells high for CD8, Tregs high for FOXP3."

**36. Line 747 — VERBOSE/VAGUE — Low**
- Current: "A diverging blue↔white↔red scale is used with a colorbar legend; … can be overlaid in each cell via **Show mean values**."
- Problem: The sentence is long, "colorbar" is US spelling, and the checkbox default is missing.
- Suggested: "**Colour** shows each marker's z-score across classes: red means the class is higher than other classes for that marker, blue means lower. This compares classes within one marker, not brightness between markers. Grey means no cells of that class had a value. **Show mean values** (on by default; IntensityHeatmapView.java:131) prints the mean in each square."

**37. Lines 757-760 — UNCLEAR — Low**
- Current: "**Export PNG** … run QuPath cell detection / intensity measurement first … (or apply gating)"
- Problem: The button label is wrong, and neither the QuPath step nor gating is linked to a section or menu.
- Suggested: "**Export as PNG…** (IntensityHeatmapView.java:134)… If your cells have no `<marker>: Cell: Mean` measurements, run *Analyze → Cell detection → Cell detection* in QuPath first. Classes are taken from the current cell classifications, so run a classifier or gating (§X) before opening the heatmap."

**38. Lines 772-776 — DEVELOPER — Medium**
- Current: "| Backed by | QuPath `DistanceTools.detectionToAnnotationDistancesSigned` | … The extension (spatially indexed; see below)"
- Problem: The method names do not help the user.
- Suggested: Delete the "Backed by" column. Add a note that all three boxes are ticked by default (DistanceMeasurementsDialog.java:144-146).

**39. Lines 786-787 — VERBOSE — Medium**
- Current: "before computing, the extension scans every cell. … This makes interrupted runs cheap to resume. It is **all-or-nothing per image**…guaranteeing internally consistent results."
- Problem: Long, and "cheap" is informal.
- Suggested: "- **Persist this pixel size to each image's calibration on save** (off by default): saves the pixel size into each image. When off, the original calibration is restored after the run.
- **Skip images where all selected measurements already exist** (on by default): skips an image only if every cell already has every selected measurement. If any measurement is missing, the whole image is recalculated. Use this to resume an interrupted run. Untick it to recalculate everything, e.g. after changing classes."

**40. Lines 788-791 and 809 — VAGUE/duplicate — High**
- Current: "**Parallel image workers** (1–N cores) … heavy distance maths … hundreds of thousands of cells" and line 809 "small images (10-20K cells) … large images (500k+ cells) use one or 2 workers."
- Problem: The default is not stated, "heavy maths" is informal, and the two passages use different numbers for the same advice.
- Suggested (replace both): "- **Parallel image workers**: how many images are processed at the same time. The range is 1 to the number of processors; the default is half the processors, up to 4 (DistanceMeasurementsDialog.java:196-199). Each image already uses all processors for the calculation, so more workers mainly overlaps loading and saving. Many images of about 10,000–20,000 cells: use more workers. Images of 500,000 or more cells: use 1–2 workers, which also uses less memory." Then delete the note at line 809.

Total: 40 (11 high, 18 medium, 11 low)

---

## Part C — lines 813-1292: §11 Scatter plot / clustering (11.1-11.6), §12 Exporting, §13 Utility scripts

### Overall patterns (lines 813-1292)
- **Several statements in §11 no longer match the code**, and users act on them. Opening the window does not compute anything. Picking project images does not sample or fit. **Re-sample** does not cluster. **Images…** does not re-sample. The Annotation filter works in both scopes. Current-image scope clusters at most the **Sample:** cap (default 50,000), not every cell. These are the most important fixes.
- **Controls are missing from §11.1.** **Sample multiple seeds**, **Reduce dims (PCA)** / **PCA comps:** and the **Colour cells in image:** row (**By cluster** / **By classification**) are not listed, or are only mentioned deep inside callouts.
- **§11.5-11.6 has a lot of developer and algorithm detail.** Examples are HNSW, CWTS, CPM, cell UUIDs, `sc.tl.ingest`, distance concentration, Smile eigendecomposition, JavaCPP and `--add-opens`. This roughly doubles the length and does not change what the user clicks.
- **The same points are made two or three times.** Normalisation appears at 826 and 1007. k-means vs Leiden assignment appears at 969, 1002, 1027 and 1160. The scope caveats are repeated as well. Two callouts (1181-1188) sit at the end of §11.6 but belong to §11.5. One starts "Even so", which refers to nothing.
- **Defaults are missing or vague.** k = 8, seeds = 10, graph neighbours = 15, the PCA threshold, the pre-filled values in the Filter Cells dialog, and the Export Regions defaults are all absent. Two menu items carry a `[TEST]` prefix that the guide does not mention.
- §12 and §13 are mostly clear. The main problems there are a few history notes for developers, one figurative heading, and one accuracy caveat in Filter Cells.

### Suggestions

**1. Lines 815-824 — UNCLEAR — High**
- Current: "When you open it you first pick which measurements to embed … The window then computes an initial embedding on a background thread."
- Problem: Nothing is computed when the window opens. It loads the cells, subsamples them to the Sample cap and waits for **Recompute** (ScatterPlotView.java:795-809). The intro is also wordy.
- Suggested: "**Extensions → SP Classify → Scatter Plots and Clustering...** opens a scatter plot for unsupervised clustering. Cells are clustered on their marker measurements with k-means or Leiden (§11.6) and drawn on a 2D PCA or UMAP plot. You can then name the clusters as QuPath classes. This does not use or change the trained classifier or its training labels.
When the window opens, pick the measurements to use in the *Select Measurements for Scatter Plot* dialog. The window then loads the open image's cells (up to the **Sample:** cap, default 50,000) and shows *"… cell(s) loaded — click "Recompute" to cluster."* Click **Recompute** to cluster and draw the plot." (ScatterPlotView.java:561, 805-809; MenuItemFactory.java:121)

**2. Lines 826-829 and 1007-1012 — VERBOSE — Medium**
- Current: "Clustering applies any **feature normalisation** you've configured … The normalizer is captured when the window opens; reopen the plot after changing it." (repeated almost word for word at 1007-1012)
- Problem: The same note appears twice and contains the internal word "normalizer".
- Suggested (keep at 826 only, delete 1007-1012): "> Clustering uses the normalisation set in **Clustering Normalisation** (§4.2). The classifier always uses raw values. Each marker is then z-scored over the cells being clustered. If you change the normalisation, close and reopen this window to apply it."

**3. Lines 834-841 — VAGUE / incorrect — High**
- Current: "by default UMAP *plots* a 20,000-cell sample … (k-means still clusters **all** cells; the status bar shows e.g. *"309,584 clustered · 19,432 plotted"*)"
- Problem: k-means clusters the loaded rows, not all cells, and the loaded rows are capped at **Sample:** (default 50,000). The 309,584 example can only happen if the user raised the cap. The phrase "the status bar shows" is also unverified: no "clustered ·" format string was found in ScatterPlotView.java.
- Suggested: "- **Embedding** — `PCA` (fast) or `UMAP` (slower; often separates overlapping populations better). The embedding only positions the points on the plot. Clustering always uses the marker values, not the 2D coordinates.
- **Full UMAP** (UMAP only) — by default UMAP plots a random 20,000 of the loaded cells. All loaded cells are still clustered. Tick **Full UMAP** to plot every loaded cell. This is slower and uses more memory. PCA always plots every loaded cell." (ScatterPlotView.java:96, 334-339)

**4. Lines 845-847 — VAGUE — Medium**
- Current: "**Clusters (k)** — number of k-means clusters (2–50). The legend shrinks to keep all clusters visible and clickable."
- Problem: The default value is not given, and the sentence about the legend does not help the user act.
- Suggested: "- **Clusters (k)** — number of k-means clusters, 2–50, default 8. Shown only when Method = k-means. Leiden uses **Resolution** instead (§11.6)." (ScatterPlotView.java:349)

**5. Lines 848-850 — UNCLEAR / incorrect — High**
- Current: "It does **not** re-sample — use **Images…** in project scope for that."
- Problem: **Images…** only picks images and does not sample (ScatterPlotView.java:583-587, 2539-2550). In project scope, Recompute draws a sample first if none is loaded (ScatterPlotView.java:930-938).
- Suggested: "- **Recompute** — runs the clustering and the embedding on the cells currently loaded. It does not draw new cells (use **Re-sample** for that). In project scope, if no sample has been drawn yet, Recompute draws one first and then clusters it."

**6. Lines 851-856 — UNCLEAR / incorrect — High**
- Current: "*Current image* (default) clusters every cell of the open image … Switching to *Project* reveals an **Images…** button and a **Sample:** spinner."
- Problem: Current-image scope loads a random subsample of up to the **Sample:** cap (ScatterPlotView.java:819-830). The Sample spinner and Re-sample are visible in both scopes. Only **Images…** is project-only (ScatterPlotView.java:598-616).
- Suggested: "- **Scope: Current image / Project** — *Current image* (default) clusters cells from the open image. If the image has more cells than the **Sample:** cap, a random subset of that size is used, and the status bar shows *"Subsampled X of Y cell(s)"*. *Project* clusters a sample pooled from several images (§11.5) and adds an **Images…** button.
- **Sample:** (1,000–5,000,000, default 50,000) — the maximum number of cells to load. Applies in both scopes. Press Enter or click **Re-sample** to apply a new value." (ScatterPlotView.java:561-566, 805-809)

**7. Lines 857-859 — UNCLEAR / incorrect — High**
- Current: "**Re-sample** — draw a fresh random sample of cells at the current **Sample:** cap and re-fit … Unlike **Recompute**, which re-fits on the *existing* rows."
- Problem: Re-sample does not cluster. Its tooltip says "Does not cluster — click Recompute to cluster" (ScatterPlotView.java:589-594, 2577-2581). The last sentence is also a fragment.
- Suggested: "- **Re-sample** — draws a new random set of cells up to the **Sample:** cap: from the chosen images in project scope, or from the open image in current-image scope. It does not cluster. Click **Recompute** afterwards."

**8. Lines 860-866 — VERBOSE — Medium**
- Current: "start over from scratch: re-opens the *Select Measurements* dialog … You only need this to **change the inputs** — the plot now **remembers its clustering between closing and reopening** …"
- Problem: One long sentence with dash asides, and the word "now" refers to an older version.
- Suggested: "- **New clustering session** — reopens the *Select Measurements* dialog so you can choose a different set of measurements, then starts a new plot. If you only close the window and reopen it from the menu, the previous clusters, scope and settings are restored without re-clustering."

**9. Lines 869-872 (and 988) — UNCLEAR / incorrect — High**
- Current: "type a keyword … *Current-image scope only* — it is disabled in project scope, since annotations belong to one image's hierarchy."
- Problem: The filter works in both scopes, with each image filtered by its own annotations. It also accepts comma-separated keywords (ScatterPlotView.java:482-490, 2462-2467). Line 988 repeats the wrong claim.
- Suggested: "- **Annotation** — enter one or more comma-separated keywords (e.g. `Tumour, Stroma`). Only cells whose centroid lies inside an annotation whose name or class contains a keyword are clustered. Leave blank to use all cells. In project scope each image is filtered by its own annotations. Press Enter to re-run."
  At 987-989, change to: "The plot works as in current-image scope, except that box, lasso and legend selection only highlight points on the plot (§11.2)."

**10. Lines 885-893 — UNCLEAR (missing controls) — Medium**
- Current: the Bottom row lists Colour by, Select, Apply/Assign and Export PNG only.
- Problem: **Sample multiple seeds**, **Reduce dims (PCA)** / **PCA comps:** and the **Colour cells in image:** row are not listed. "By cluster (all images)" at 1032 is never introduced.
- Suggested (add these bullets):
  "- **Sample multiple seeds** — runs the clustering 10 times from different starting points with a fixed seed and keeps the best result. Repeated runs with the same settings then give identical clusters. Applies to k-means and Leiden. Off: one faster run whose cluster numbers can differ between runs. (ScatterPlotView.java:118-126, 380-386)
  - **Reduce dims (PCA)** (on by default) and **PCA comps:** (2–500, default 50) — when more than 50 measurements are selected, the data are reduced to this number of principal components before clustering. With 50 or fewer it has no effect. (ScatterPlotView.java:388-404; ScatterMath.java:126,133)
  - **Colour cells in image: By cluster / By classification** — **By cluster** colours every cell in the open image by its nearest cluster and writes a numeric `Cluster` measurement. It does not change the cell's classification, and the overlay is removed when the window closes. In project scope the button reads **By cluster (all images)** and writes and saves `Cluster` in every selected image. **By classification** returns to QuPath's class colours." (ScatterPlotView.java:673-686, 2475-2477)

**11. Lines 911-915 — FIGURATIVE — Medium**
- Current: "a **per-cluster marker heatmap** (mean z-scored intensity: **red = high, blue = low** — the cluster's phenotype fingerprint, so you can name it from its high markers)"
- Problem: "Phenotype fingerprint" is a metaphor, and the sentence is overloaded.
- Suggested: "It shows one row per non-empty cluster with: a colour swatch, the cell count, a heatmap of the cluster's mean z-scored value for each marker (red = high, blue = low), and a dropdown. Use the high markers to decide the name. In the dropdown, pick an existing class, type a new class name, or choose **— skip —**."

**12. Lines 952-957 — DEVELOPER DETAIL — Medium**
- Current: "PCA and UMAP use native math libraries (OpenBLAS / ARPACK via JavaCPP). The extension opens the required JVM module access automatically …"
- Problem: Library and JVM internals. The only user-relevant part is the fallback message. The note is also placed in §11.4, which is about gating.
- Suggested (move to the end of §11.1): "> If the status bar shows *"(UMAP unavailable — showing PCA)"*, UMAP could not start on this computer. Restart QuPath with the launch option `--add-opens=java.base/java.lang=ALL-UNNAMED` to enable it." (ScatterPlotView.java:1205-1214)

**13. Lines 961-970 — VERBOSE / DEVELOPER DETAIL — Medium**
- Current: "To cluster a **whole cohort consistently**, flip the **Scope** toggle … It all happens in the same window, so every tool — colour-by-marker, within-class gating … **Leiden** assigns by kNN label transfer …"
- Problem: Long, with dash asides and method internals that §11.6 already covers.
- Suggested: "Project scope fits one clustering on a sample of cells pooled from the images you choose, then applies it to every cell in those images. Cluster 3 then means the same population in every image. (If you cluster each image separately, the cluster numbers cannot be compared between images.) All controls in §11.1 work in project scope except the viewer selection (§11.2)."

**14. Lines 974-978 — UNCLEAR / incorrect — High**
- Current: "2. The extension streams each image and pools a bounded random sample … then fits k-means and draws the plot."
- Problem: After you pick images, nothing is sampled. The status bar shows "Picked N image(s) — click "Re-sample" to draw a sample, then "Recompute" to cluster." (ScatterPlotView.java:2521-2566).
- Suggested: "1. Click **Project**. Choose the images to sample (all are selected by default). Click Cancel to stay on the current image.
2. Click **Re-sample**. The extension reads each image and takes up to **Sample:** ÷ (number of images) random cells from each, up to 50,000 in total by default. The status bar shows *"Sampled X cell(s) across N image(s)"*.
3. Click **Recompute** to cluster the sample and draw the plot. (Clicking Recompute straight after step 1 does steps 2 and 3 together.)" (CohortClusterModel.java:152; ScatterPlotView.java:930-938)

**15. Lines 980-983 — VAGUE / marketing — Low**
- Current: "50,000 cells is statistically ample to place stable centroids (more barely move them but cost time) … memory stays flat regardless of project size."
- Problem: "statistically ample" and "barely move" are not specific, and no supporting evidence is cited.
- Suggested: "The sample is only used to fit the clusters. When you click **Assign Clusters…**, every cell in every selected image is assigned, one image at a time, so the number of images does not limit memory use. Raising **Sample:** above 50,000 makes the fit slower."

**16. Lines 995-996 — UNCLEAR / incorrect — High**
- Current: "To draw a fresh sample — different images, or a new **Sample:** size — click **Images…**."
- Problem: **Images…** only re-picks images and clears the plot. **Re-sample** draws the sample (ScatterPlotView.java:2539-2550).
- Suggested: "- **Recompute** clusters the current sample again. To use different images, click **Images…** and then **Re-sample**. To change the sample size, change **Sample:** and click **Re-sample**. Then click **Recompute**."

**17. Lines 1000-1005 — VERBOSE / style — Low**
- Current: "On confirm, The extension streams each selected image, assigns all matching cells to their cluster (nearest centroid for k-means; kNN label transfer …"
- Problem: Capital "The" mid-sentence, and the method detail repeats §11.6.
- Suggested: "Click **Assign Clusters…**. The dialog from §11.3 opens. After you confirm, each selected image is opened in turn, every matching cell is given its cluster's class, and the image is saved. Progress is shown in the status bar. (For how cells outside the sample are assigned, see §11.6.)"

**18. Lines 1014-1060 — DEVELOPER DETAIL / VERBOSE — High**
- Current: "the exact, true-scanpy `sc.tl.leiden`-style mode: **every** cell … one approximate-NN (HNSW) kNN graph … single CWTS Leiden partition … stable cell UUID (not by iteration order …)"
- Problem: About 45 lines of internals. It is also unclear what this mode writes: a `Cluster` measurement (per 1047-1053), not classes as **Assign** does. The default, the warning limit and the Cancel behaviour are the parts users need.
- Suggested (full replacement): "**Leiden in project scope: Cluster all cells / Transfer from sample**
With **Method = Leiden** and **Scope = Project**, two options appear next to Method:
- **Cluster all cells** (default) — clusters every cell in every selected image together, not just the sample. This is slower and uses more memory.
- **Transfer from sample** — clusters only the sample. Each other cell takes the most common cluster among its nearest neighbours in the sample. This is faster.
With **Cluster all cells** selected, clicking **Assign Clusters…** or **By cluster (all images)**:
- first counts the cells. If there are more than 50,000,000, it asks you to confirm before continuing (ScatterPlotView.java:133);
- shows each step in the status bar: *Pooling 12/40 images → Building kNN graph… → Running Leiden… → Writing 12/40 images*;
- stops without writing anything if the neighbour search is not accurate enough (below 95% recall). Existing `Cluster` values are kept. Try different markers or fewer cells;
- shows a **Cancel** button. Cancelling stops before the next image. Images already written keep their new `Cluster` values.
When it finishes, the legend and image overlay show the number of clusters found across all cells. The plot itself still shows the sample."
  (Unverified: whether **Assign Clusters…** in this mode writes classes as well as `Cluster`. Check before publishing.)

**19. Lines 1062-1067 — DEVELOPER DETAIL — Medium**
- Current: "Single-image Leiden … also builds its kNN graph through the same HNSW approximate-NN index now, rather than a brute-force scan — this is transparent …"
- Problem: An implementation change described in a long sentence. Only the error message matters to the user.
- Suggested: "If Leiden on a single image or a project sample shows *"Leiden preview: ANN recall too low — try more cells / different markers."*, no clusters were made. Increase **Sample:** or change the ticked **Cluster markers** and click **Recompute**." (ScatterPlotView.java:1073-1074)

**20. Lines 1069-1085 — DEVELOPER DETAIL — Medium**
- Current: "Two remaining documented gaps (a third — PCA — is now implemented, see below): 1. **Quality function** — … **Constant Potts Model (CPM)** … RBConfiguration …"
- Problem: Algorithm comparison aimed at developers. It also uses "knob".
- Suggested: "> **Comparison with scanpy.** Results are similar to `sc.tl.leiden` in Python but not identical. This extension uses a different quality function (CPM rather than modularity) and weights the neighbour graph by shared neighbours (Jaccard) rather than UMAP connectivities. The same **Resolution** value can give slightly different cluster boundaries in scanpy." (Move the full detail to CLAUDE.md, which already has it.)

**21. Lines 1087-1108 — VERBOSE / DEVELOPER DETAIL — Medium**
- Current: "Below ~50 active marker columns this is a no-op (a small, curated panel is already low-dimensional — projecting onto ≥ p components is just a lossless rotation) …"
- Problem: A 22-line callout of internals (distance concentration, Smile, seeded subsample). The threshold is given as "~50".
- Suggested (replace the whole callout; the control is described in suggestion 10): "> **Reduce dims (PCA).** When more than 50 measurements are ticked, clustering is run on the first **PCA comps:** principal components (default 50) instead of on every measurement. This stops a marker that has many measurement columns (mean, median, nucleus, cytoplasm, etc.) from dominating the result. The heatmap in the assignment dialog still shows the original marker values. When PCA is used, the status bar shows e.g. *"PCA: 240 → 50 comps, 87.3% variance"*." (ScatterPlotView.java:392, 1229-1233; ScatterMath.java:126,133)

**22. Lines 1120-1135 — VERBOSE — Medium**
- Current: "everything else in this section … works identically for both, because both ultimately produce the same per-cell cluster label array … it traces back to PhenoGraph …"
- Problem: Internal reasoning ("label array") and history that the user does not need.
- Suggested: "All other controls work the same for both methods.
- **k-means** (default) splits cells into exactly **k** clusters. It works best when populations are of similar size.
- **Leiden** links each cell to its 15 nearest cells (by marker values) and finds groups of closely linked cells. You set **Resolution** instead of a cluster count, and the number of clusters comes from the data. It is better than k-means at keeping small or unevenly sized populations as separate clusters. This is the method used by scanpy, scimap and SPACEc." (ScatterPlotView.java:116)

**23. Lines 1147-1152 — VAGUE / incorrect — High**
- Current: "**Sample multiple seeds** (checkbox) — mirrors k-means' multi-restart reproducibility: when ticked, Leiden runs several random-seeded passes … (the same *populations* are still found …)"
- Problem: The checkbox applies to both methods and is always visible. "Several" is 10. The claim in brackets that the same populations are always found is not guaranteed.
- Suggested: "- **Sample multiple seeds** — see §11.1. It applies to both methods: when ticked, the clustering runs 10 times with a fixed seed (42) and keeps the best result. When unticked, cluster numbers, and sometimes boundaries, can change between runs." (ScatterPlotView.java:118-126, 380-386, 418-420)

**24. Lines 1154-1156 — VAGUE — Medium**
- Current: "The kNN graph-neighbour count and edge-weighting scheme are fixed, sensible defaults … see the design note in the repository for the full recipe and rationale."
- Problem: "Sensible" is vague, and "the design note in the repository" does not say which document.
- Suggested: "The neighbour count (15) and the edge weighting (shared-neighbour Jaccard) are fixed and cannot be changed in this version." (ScatterPlotView.java:116)

**25. Lines 1160-1175 — VERBOSE / FIGURATIVE — Medium**
- Current: "Leiden has no centroids to assign new cells to — averaging a non-spherical community into one point would defeat the method …"
- Problem: Repeats §11.5 for the third time and uses figurative phrasing ("first-class member", "defeat the method").
- Suggested: "**How cells outside the sample are assigned (project scope)**
- **k-means:** each cell joins the cluster with the closest mean.
- **Leiden, Transfer from sample:** each cell takes the most common cluster among its 15 nearest cells in the sample.
- **Leiden, Cluster all cells:** all cells are clustered together, so no assignment step is needed (§11.5)."

**26. Lines 1177-1188 — UNCLEAR (misplaced) — High**
- Current: "Both methods otherwise share the exact same pipeline … > Even so, per-marker normalisation does not fully correct **per-image** staining differences … Normalise upstream if intensity scales differ a lot … > **This writes classifications and saves every selected image.**"
- Problem: Lines 1177-1180 repeat earlier text. The two callouts belong under "Assigning across the cohort" in §11.5. "Even so" refers to nothing, and "a lot" is vague.
- Suggested: delete 1177-1180. Move these two callouts to just after line 1005:
  "> **This changes every selected image.** Assigning replaces the class on the assigned cells and saves each image. Training labels are not changed. The open image updates immediately.
  > **Staining differences between images.** Normalisation is applied per marker, not per image. If one slide is stained brighter than the others, its cells can fall into different clusters. Check the per-image intensity distributions (or use batch normalisation) before pooling."

**27. Line 1212 — VERBOSE — Medium**
- Current: "It mirrors the *Select Features* dialog — search box, prefix dropdown … so text labels that aren't numeric measurements can now be exported too; filter for them by name if the list is long …"
- Problem: A single 140-word paragraph, and "now" refers to an earlier version.
- Suggested: "Before exporting, the **Select Columns for Cell Table Export** dialog opens. It works like *Select Features*: search box, prefix dropdown, **Select Prefix** / **Clear Prefix**, **Select All** / **Clear All**. The whole-cell mean measurements and any distance measurements are ticked by default. Numeric measurements are listed first, followed by text fields such as `CN Class`. To add cell outlines, tick **Export cell polygons (geometry)** and choose **Microns (µm)** or **Pixels** under **Units**. Any value a cell does not have is written as `NA`." (CellTableExportPane.java:47,129,186)

**28. Line 1230 — DEVELOPER DETAIL — Low**
- Current: "(Earlier versions offered a normalised `__norm` column set; that was removed when normalisation became clustering-only.)"
- Problem: A version-history note.
- Suggested: "Exports raw feature values (the values the classifier uses) for labelled cells only."

**29. Line 1245 — DEVELOPER DETAIL — Low**
- Current: "The `.planning/phases/12` document scopes a bundle format as a future feature."
- Problem: Points users to an internal planning file.
- Suggested: "> Ground truth can only be exported and imported as single CSV files. There is no ZIP bundle option."

**30. Line 1253 — FIGURATIVE — Low**
- Current: "A grab-bag of common housekeeping operations that would otherwise live in one-off Groovy scripts."
- Suggested: "Tools for common cleanup tasks. Each one asks for its settings, then reports what it changed."

**31. Line 1257 — VAGUE / accuracy — High**
- Current: "A dialog takes an optional **Min** and **Max** for both **Cell area (µm²)** and **Circularity** — leave any field blank for no bound."
- Problem: The dialog opens with **Max area = 500.0** and **Min circularity = 0.7** already filled in (UtilityScripts.java:86-91). A user who clicks OK applies these. The tool also uses the first measurement whose name contains "area" or "circularity" (UtilityScripts.java:139-140, 192-200). That might be a nucleus measurement rather than the cell measurement (unverified for typical QuPath detection output).
- Suggested: "Removes cell detections from the **current image** that fall outside size and shape limits. The dialog has **Min** and **Max** boxes for **Cell area (µm²)** and **Circularity (0–1)**. It opens with Max area = 500 and Min circularity = 0.7. Clear a box to remove that limit. A cell is removed if it breaks any limit. The tool uses the first measurement whose name contains "area" (or "circularity"). Check which one that is in your cell measurements. Cells without both measurements are kept. The number of cells to be removed is shown before anything is deleted."

**32. Lines 1269-1273 — UNCLEAR / DEVELOPER DETAIL — Medium**
- Current: "### 13.4 Import GeoJSON Objects … (off by default — it is O(n²) and slow for many objects). Parsing streams the file feature-by-feature on a background thread …"
- Problem: The menu label is **[TEST] Import GeoJSON Objects...** (strings.properties:31). "O(n²)" is jargon, and the parsing detail is internal.
- Suggested: "Menu: *Utility Scripts → [TEST] Import GeoJSON Objects...* Imports annotations and detections from a `.geojson` or `.geojson.gz` file into the **current image**. Options (both off by default): **Clear existing objects first**, and **Resolve hierarchy after import**. The second can take a long time with many objects. Annotations are added and locked first, then detections, and the image is saved." (UtilityScripts.java:748-749)

**33. Line 1279 — VAGUE — Medium**
- Current: "set the **downsample**, **tile size**, **writer threads**, **compression** (LZW by default), and whether to write **BigTIFF** and a **pyramid** …"
- Problem: Only one default is given, and the `[TEST]` menu prefix is not mentioned.
- Suggested: "Menu: *Utility Scripts → [TEST] Export Annotation Regions...* Exports annotations from the **current image** as OME-TIFFs. Pixels outside each annotation's outline are set to 0. Enter annotation names separated by commas, or leave blank to export all annotations. Defaults: **Downsample** 1.0, **Tile size (px)** 512, **Writer threads** = number of CPU cores (maximum 32), compression **LZW**, **BigTIFF** on, **Build pyramid** on. Each region is saved as `<image>__<annotation>.ome.tif` in the folder you choose, and a notification reports how many succeeded." (AnnotationRegionExporter.java:97-106; strings.properties:32)

**34. Line 1287 — FIGURATIVE — Low**
- Current: "**Safety net:** before deleting anything, a timestamped …"
- Suggested: "**Backup:** before deleting, the extension writes `celltune_backup_<timestamp>.zip` to the project folder. To undo the reset, unzip it into the project folder. This recreates `celltune/`. To confirm the reset, type `RESET`." (SpClassifyExtension.java:830, 851)

Total: 34 (14 high, 15 medium, 5 low)

---

## Part D — lines 1293-1723: §14 Sidebar/preferences reference, §15 Menu reference, §16 Project layout, §17 Pixel prescreen, §18 Cellular neighborhoods, §19 Batch normalisation, §20 Tips

### Overall patterns (lines 1293–1723)
- §17–§19 are written partly for developers: formulas, Java/JTS class names, preference keys, and "why we built it this way" notes are mixed into the user steps. Most of this can be cut or moved to CLAUDE.md.
- Some facts are wrong or missing compared with the source code. The §15 menu table leaves out **Cellular Neighborhoods...** and **Lock All Annotations**. §14 leaves out the Auto-tune **Trials** and **CV folds** spinners. The prescreen memory caveat says one image at a time, but the code reads up to 4 at once. It also tells users to change the 2048 px target, which is not in the UI.
- There is a lot of figurative or casual wording: "Safety net", "cousin", "twin", "dice roll", "sweet spot", "lights up", "knob", "kills the whole point", "F1 scores can lie", "dial it down", "blow-up".
- The same point is often made more than once. Examples: "non-destructive / `getPathClass()` untouched" appears three times in §18. "Metadata because measurements are numeric" appears twice. The max_bin advice appears twice.
- Image captions in §18 are long and partly describe old screenshots. Several §20 tips are grammatically broken or unclear (Selecting cells, Channel Viewer, Resolve hierarchy).

### Suggestions

**1. Lines 1343–1372 — UNCLEAR INSTRUCTIONS — High**
- Current: the menu table has no row for *Cellular Neighborhoods...* or *Lock All Annotations*, and lists "Import GeoJSON Objects..." / "Export Annotation Regions...".
- Problem: the "every menu item" table leaves out two real items, and the names of two others do not match what users see.
- Suggested: add these rows: "| Cellular Neighborhoods... | Project, cells with classifications | Group cells by the cell types around them (k-means on neighbourhood composition). See §18. |" and "| Utility Scripts ▸ Lock All Annotations | Open image or project | Lock every annotation so it cannot be moved or edited by accident. |". Rename the GeoJSON and region items to "[TEST] Import GeoJSON Objects..." and "[TEST] Export Annotation Regions...", which are the labels in the menu. (strings.properties:13, 28, 31, 32; MenuItemFactory.java:75–77, 123)

**2. Lines 1306 — VAGUE — High**
- Current: "Automatically searches for better settings by training **200 models**. Hours on a big panel — plan for overnight."
- Problem: 200 is not fixed. It comes from two sidebar spinners (Trials, CV folds) that are missing from the table.
- Suggested: "| **Auto-tune hyperparameters** | ❌ | Searches for better model settings. Cost = 2 × Trials × CV folds model fits (200 at the defaults). This can take several hours on a panel with many features. |" and add two rows: "| **Trials** | 20 | Settings combinations tried per model (5–100). Shown only when Auto-tune is ticked. |" and "| **CV folds** | 5 | Cross-validation folds used to score each combination (2–10). |" (ClassificationPanel.java:252–271; HyperparameterTuner.java:34, 37)

**3. Lines 1519–1524 — VAGUE / incorrect — High**
- Current: "One downsampled image (all channels) is held in memory at a time; … the 2048 px target is the place to dial it down if needed."
- Problem: up to 4 images are read at the same time, and users cannot change the 2048 px target anywhere in the UI.
- Suggested: "**Caveats.** With fewer than about 5 images, the z-scores are unreliable. Saturation is measured against the storage bit depth, so a 12-bit image stored as 16-bit is compared with 65535. Floating-point images show saturation as `n/a`. Up to 4 images are read at once, each at about 2048 px on the long edge with all channels, so panels with many channels need more memory. This size cannot be changed in the dialog." (AnalysisViews.java:333; ImagePixelStatsReader.java:36)

**4. Lines 1427–1432 — DEVELOPER DETAIL / VAGUE — Medium**
- Current: "requested downsample = `longEdge / 2048` … Images are **read in parallel** (a small fixed thread pool) so large projects scan several-fold faster."
- Problem: the formula and "thread pool" are internals, and "several-fold" is vague.
- Suggested: "1. Each image is read at the pyramid level closest to 2048 px on its long edge, so every image is compared at the same size. Up to 4 images are read at once." (AnalysisViews.java:333)

**5. Lines 1413–1421 — VERBOSE / FIGURATIVE — Medium**
- Current: "A **prescreen you run at the very start of a project** … It is the pixel-level twin of the Project Prediction Summary…"
- Problem: the paragraph repeats itself, includes a sentence with a broken line wrap, and uses the "twin" metaphor.
- Suggested: "Run this at the start of a project, before segmentation. It reads a low-resolution copy of every image, measures pixel intensities for each channel, and flags images that differ from the rest of the project: mostly background, saturated, weakly stained, or unusually bright or dim. Use it to decide which images to fix, exclude, or label more heavily later. It does not need cells. The [Project Prediction Summary](#8-project-prediction-summary) does a similar check after classification."

**6. Lines 1449–1466 — FIGURATIVE / DEVELOPER DETAIL — Low**
- Current: "mean's outlier-resistant cousin" / "`Σx / N`" / "variance of the discrete Laplacian"
- Problem: there is a metaphor, and the Definition column is formula-heavy for this audience.
- Suggested: median row: "Middle pixel value. Used for sorting and comparison because single bright pixels do not change it." You could also drop the Definition column and keep only "What it tells you".

**7. Lines 1482–1486 — VERBOSE / VAGUE — Medium**
- Current: "cohort-median foreground coverage clears a small floor (~5%) … their meaningless relative jitter can't manufacture false 'outlier' flags."
- Problem: "~5%" is actually exactly 5%, and the sentence uses figurative wording.
- Suggested: "> Only channels with a median foreground coverage of at least 5% across the project are checked for intensity outliers. Channels with almost no signal are skipped, so they cannot produce false flags. Focus is shown but never flags an image." (PixelCohortAnalyzer.java:44)

**8. Lines 1534 — VERBOSE — Medium**
- Current: "**The purpose of the clustering.** A per-cell phenotype tells you *what a cell is*; it says nothing about *where it sits*. Two CD8 T cells…"
- Problem: this repeats the previous paragraph and §18.1 and runs to about 110 words.
- Suggested: "Use this when cells of the same type behave differently depending on where they are, for example CD8 T cells inside tumour compared with CD8 T cells at the invasive margin. Instead of drawing tumour, stroma and interface regions by hand, k-means finds recurring neighbourhood compositions and labels every cell with one. You get a map in the viewer and, for each image, the fraction of cells in each neighbourhood, which you can compare across the cohort."

**9. Lines 1536 — VERBOSE (repeated) — Low**
- Current: "It is fully **non-destructive**: the CN id is written as a numeric `CN` measurement (and, once you name them, a `CN Class` text label…"
- Problem: this repeats the storage table at 1615–1621, and `getPathClass()` is developer jargon.
- Suggested: "Results are written as new measurements (see §18.5). Cell classifications are not changed. You need cells that already have classifications, either from running the classifier or from an import."

**10. Lines 1548 — VERBOSE — Medium**
- Current: "The **window (cells)** spinner sets the **total window size**: with **Include centre cell** on the window is the centre cell plus its nearest neighbours; … (The spinner counts total cells; internally…)"
- Problem: the same rule is explained three times in one bullet.
- Suggested: "**k nearest neighbours**: the window is a fixed number of cells (**window (cells)**, default 10, range 2–100). With **Include centre cell** ticked, a window of 10 is the cell itself plus its 9 nearest neighbours, as in Schürch et al. With it unticked, it is the 10 nearest neighbours." (NeighborhoodAnalysisDialog.java:322)

**11. Lines 1549 — VAGUE — High**
- Current: "**within radius** — every cell within a fixed radius (in µm when calibrated, else px)."
- Problem: no default or range is given.
- Suggested: "**within radius**: every cell within a set distance (default 50, range 5–500; µm if the image is calibrated, otherwise pixels)." (NeighborhoodAnalysisDialog.java:330)

**12. Lines 1550–1552 — DEVELOPER DETAIL / VERBOSE — Medium**
- Current: "…(adapts per image across a mixed-density cohort — the convention used by Giotto's Delaunay network)…All three use a spatially-indexed search (JTS `STRtree`…"
- Problem: library names and indexing details do not change what the user does.
- Suggested: "**Delaunay triangulation**: neighbours are the cells directly connected to it in a triangulation, so the window size follows local cell density. Long connections across empty space are removed. Choose **max edge** for a fixed limit (default 50 µm, range 1–2000) or **auto (Q3+1.5·IQR)** to set the limit separately for each image from its own connection lengths. A cell with no remaining connections gets `CN = -1`." Then delete line 1552. (NeighborhoodAnalysisDialog.java:344)

**13. Lines 1554 — VERBOSE / DEVELOPER DETAIL — Low**
- Current: "(The paper clusters raw type *counts*; for a fixed-size kNN window that is mathematically identical to clustering fractions…)"
- Problem: this justifies the method and gives the user nothing to do. Move it to CLAUDE.md.
- Suggested: delete the parenthetical.

**14. Lines 1556 — VAGUE — Medium**
- Current: "By default k-means is run several times from different seeds…"
- Problem: "several" is a fixed number.
- Suggested: "With **Sample multiple k-means seeds (more reproducible)** ticked (the default), k-means runs 10 times and keeps the best fit." (NeighborhoodModel.java:482; NeighborhoodAnalysisDialog.java:231)

**15. Lines 1560 — FIGURATIVE / VERBOSE — Medium**
- Current: "**Raw vs standardized (the most important knob).** … pulls out **specific immune niches** far more sharply…"
- Problem: "knob" is figurative, and the paragraph is long.
- Suggested: "> **Standardize compositions before clustering** has the largest effect on results. Off (default, as in the paper): clusters separate the main tissue structure (tumour, stroma, interface). On: each cell type is scaled equally, so rare immune populations get their own clusters, but tumour and stroma merge into one or two large clusters. To get both, tick it, set **Number of CNs** to 12–15, and merge duplicate tumour clusters afterwards (§18.5)."

**16. Lines 1562 — FIGURATIVE — Medium**
- Current: "a single run is a dice roll — … swung by ~0.3 (ARI) on seed alone. … run the clustering 10× … only which local optimum you land in."
- Problem: "dice roll" is figurative, and ARI and local optimum are jargon.
- Suggested: "> A single k-means run depends on its random starting point. On test data, agreement with the published neighbourhoods varied a lot between starting points. **Sample multiple k-means seeds** (on by default) runs it 10 times and keeps the best result, so repeated runs give the same answer. Untick it for a faster single run while you try out settings."

**17. Lines 1570 — VAGUE — High**
- Current: "The **Sample windows for fit** spinner caps the pool (50k is plenty for stable centroids)"
- Problem: the default and range are not stated, and the screenshot caption (1585) shows 500k, which may confuse users.
- Suggested: "**Sample windows for fit** (default 50,000, range 1,000–5,000,000) sets how many windows are used to fit the clusters. Every cell is still assigned afterwards." (NeighborhoodAnalysisDialog.java:447)

**18. Lines 1577 — FIGURATIVE / VERBOSE — Low**
- Current: "Nothing is copied between projects, so there's **no data duplication and no disk-quota blow-up** from merging `.qpdata` files."
- Suggested: "Each image's results are saved in its own project. No files are copied between projects."

**19. Lines 1585 — VERBOSE / UNCLEAR — Medium**
- Current: "(This screenshot pre-dates later changes; the kNN control now reads **window (cells)** … a fourth option, **Sample multiple k-means seeds**, has since been added…)"
- Problem: the caption mostly describes how the old screenshot differs from the current UI.
- Suggested: replace the screenshot. If that is not possible, use: "*The dialog set up for a whole-project run. The current version has an extra option, **Sample multiple k-means seeds**, and the kNN control is labelled **window (cells)**.*"

**20. Lines 1588 — VERBOSE (repeated) — Low**
- Current: "Choose the **Neighborhood window**: **k nearest neighbours** (set **window (cells)**; default `10` = a 10-cell window including the centre cell…"
- Problem: this repeats §18.2 word for word.
- Suggested: "2. Choose the **Neighborhood window** (see §18.2). Use **within radius** for a fixed physical distance on calibrated images, or **Delaunay triangulation** when cell density varies a lot."

**21. Lines 1589 — VAGUE — Low**
- Current: "Set **Number of CNs** (paper default 10)."
- Suggested: "3. Set **Number of CNs** (default 10, range 2–30). Fewer gives broader regions. More gives finer regions, some of which you may need to merge." (NeighborhoodAnalysisDialog.java:335)

**22. Lines 1593 — VAGUE — Medium**
- Current: "For project scope, set **Sample windows for fit** and **Parallel workers**."
- Suggested: "7. For project scope, check **Sample windows for fit** (default 50,000) and **Parallel workers** (default: number of CPU cores minus 1, maximum 8; see §18.6)." (NeighborhoodAnalysisDialog.java:447, 457)

**23. Lines 1602 — FIGURATIVE — Low**
- Current: "so a rare population lights up bright red even at a low absolute fraction."
- Suggested: "so a cell type that is low in absolute terms but higher than in other CNs is still shown in red."

**24. Lines 1606 — VERBOSE — Medium**
- Current: "*A finished 10-CN fit across a 42-image project. … Notice the split of outcomes: a raw-fraction fit like this resolves…*"
- Problem: a 140-word caption, with an image count that disagrees with the other caption (41 images).
- Suggested: "*A 10-CN result for a whole project. CN 8 and CN 1 are mostly tumour (32% and 20% of cells). CN 4 is stroma/other. CN 5, 7 and 10 are small immune-rich neighbourhoods (1–3% of cells). CNs 1, 2, 8 and 9 are all tumour-dominated; give them the same name to merge them (below).*"

**25. Lines 1610–1623 — VERBOSE (repeated) — Medium**
- Current: "QuPath measurements can only hold numbers, so the readable name lives in the metadata map…" appears at 1610 and again at 1623, and the "non-destructive / `getPathClass()`" point appears again at 1615.
- Suggested: keep the table and replace the bullets and the 1623 note with: "Giving two CNs the same name merges them. Only `CN Class` and `CN Class code` change; `CN` keeps the original cluster numbers. `CN Class` is stored as text in the cell's metadata, so in **Export ▸ Cell Table...** you must tick it in the column list. It is listed after the numeric measurements."

**26. Lines 1631–1636 — FIGURATIVE / VAGUE — Medium**
- Current: "Dial it back on very large slides (hundreds of thousands of cells each)… 2–4 workers is often the sweet spot."
- Suggested: "**Parallel workers** (default: CPU cores minus 1, maximum 8) sets how many images are processed at once. Each worker loads one whole image's cells, so memory use rises with the worker count. Use 2–4 workers for images with hundreds of thousands of cells. Use more for many small images. Results are the same for any worker count." (NeighborhoodAnalysisDialog.java:457)

**27. Lines 1650 — VERBOSE / FIGURATIVE — Low**
- Current: "makes the tissue architecture legible at a glance — … turning the abstract cluster ids into a map you can read against the H&E-like structure."
- Suggested: "*The **Color by: Neighborhood (CN)** overlay, with the enrichment heatmap and the **Name / merge neighborhoods** panel. Neighbouring CNs get contrasting colours.*"

**28. Lines 1663 — VERBOSE / DEVELOPER DETAIL — Medium**
- Current: "for each marker it aligns per-image log-intensity histograms by the rigid shift … conservative about erasing real biology."
- Suggested: "Staining intensity often differs between slides or runs. Batch normalisation multiplies each image's intensity for each marker by one correction factor, so that the distribution lines up with a reference. Only the position of the distribution changes, not its shape. Clustering and the classifier then see the same intensity scale across the cohort. The method is feature-level UniFORM (Wang et al., *Cell Reports Methods* 2025; see [README ▸ References](README.md#references))."

**29. Lines 1677 — UNCLEAR INSTRUCTIONS — Medium**
- Current: "*Also include projects ▸ Add project…* pools images from other SP Classify projects…"
- Problem: "▸" usually means a menu path, which is probably not the case here, and the wording differs from §18.3. The label is unverified.
- Suggested: "2. **Images**: click **Choose images…** to pick the images. To include images from another project, click **Add project…** and select its `project.qpproj`. Measurement names must match. **Clear** removes added projects."

**30. Lines 1680–1682 — VAGUE — High**
- Current: "**Workers** (images processed in parallel)." and "Per image / Per batch" with no default marked.
- Suggested: "5. **Advanced**: **Bins** (default 1024, range 64–4096), **Cells/image** (cells sampled per image for the fit, default 50,000, range 1,000–2,000,000), **Workers** (images processed at once, default CPU cores minus 1, maximum 8)." Also mark which granularity is selected by default (unverified; check BatchNormalizationDialog.java:89–90). (BatchNormalizationDialog.java:186–198; BatchNormalizerModel.java:32)

**31. Lines 1687 — VAGUE — Medium**
- Current: "Scan a few markers to confirm the batches were pulled together without collapsing genuine structure."
- Suggested: "Check several markers. After correction, the curves for each batch should overlap and the SD value should fall. If a curve that had two peaks now has one, the correction may have removed real differences."

**32. Lines 1693 — DEVELOPER DETAIL / VERBOSE — Medium**
- Current: "…applied consistently at every seam (clustering, training, and single-image auto-classify / batch-apply…). It is a persistent project preference (`celltune.useBatchCorrection`)…"
- Suggested: "**Use corrected values without writing columns (recommended)**: tick **Use batch-corrected values in clustering + ML (streamed, no columns)**. Clustering, training and classification then use corrected values in memory. The cells' measurements are not changed. The setting stays on until you untick it, and does nothing until a fit exists." (BatchNormalizationDialog.java:240)

**33. Lines 1700 — VAGUE — Low**
- Current: "(mirrors the caution in §18.8)"
- Suggested: "**It can over-correct.** If your groups really differ in intensity, for example treated and control, putting them in different batches removes that difference. Check the QC view for each marker."

**34. Lines 1331–1339 — VERBOSE / FIGURATIVE — Medium**
- Current: "**XGBoost histogram bins — leave this alone.** … painfully slow … chops them into buckets … *If you genuinely need the speed*…"
- Problem: four paragraphs (about 300 words) for a setting users are told not to change, and the table row (1329) already says the same thing.
- Suggested: replace 1331–1339 with: "**XGBoost histogram bins.** Leave this at 0 (= 256 bins, the most accurate). Lower values make XGBoost training faster (128 is about 2× faster, 64 about 2.6×) but change some predictions. To try a lower value, train once at 0 and once at 128, export the cell table both times, and compare the Training Metrics and class columns. The value used is recorded in the training log as `XGB max_bin: …`."

**35. Line 1287 context / 1299 — FIGURATIVE / VAGUE — Low**
- Current: "How many compute resources training may use. 0 = all of it."
- Suggested: "| **CPU threads** | 0 (all) | Number of CPU threads training uses; 0 = all cores. Lower it to keep QuPath responsive during training. Remembered between sessions. See §5.3. |"

**36. Lines 1405 — VAGUE / DEVELOPER DETAIL — Low**
- Current: "JSON throughout. Model bytes are Base64-encoded inside the state files. Safe to commit `celltune/` to git…"
- Problem: the directory tree also leaves out `logs/` and `batch-shifts.json`, which other sections mention, and Base64 is an internal detail.
- Suggested: add `├── batch-shifts.json   # Batch normalisation fit (§19)` and `├── logs/   # Training logs, last 20 kept` to the tree. Change the text to: "All files are JSON. You can put `celltune/` under version control to share labels and review history." (TrainingLogRecorder.java:43)

**37. Lines 1382–1383 — DEVELOPER DETAIL — Low**
- Current: "# Saved CompositeClassificationRule objects (advanced/programmatic)"
- Suggested: "# Saved composite classification rules"

**38. Lines 1708–1710 — UNCLEAR INSTRUCTIONS — High**
- Current: "Hold Ctrl key on windows/linux…" / "View > Channel viewer is very useful, it will display a view of the target area which all channels…" / "We also have noticed a bug where parent annotations are missing…"
- Problem: these are broken sentences, and the Resolve-hierarchy tip does not say what to do.
- Suggested: "**Select several cells at once:** hold Ctrl (Windows/Linux) or Cmd (Mac) while clicking cells, then apply a label." / "**Channel viewer:** *View → Channel viewer* shows the area under the cursor in every selected channel side by side." / "**Run Resolve Hierarchy before exporting.** Without it, the parent annotation column can be empty for some cells in exports. Run *Utility Scripts ▸ Resolve Hierarchy...* first (§13.2)."

**39. Lines 1711–1712 — FIGURATIVE — Medium**
- Current: "**F1 scores can lie.** … before believing the metrics." / "Two XGBoosts won't disagree much, which kills the whole point."
- Suggested: "**Training Metrics overestimate accuracy on new images.** The 20% held-out cells come from the same images used for training. Check predictions on a few images with no labels before relying on the scores." / "**Use different model types for Model 1 and Model 2.** Two models of the same type rarely disagree, so Review Mode finds few cells to review."

**40. Lines 1714 — VAGUE — Low**
- Current: "Every run ends with a "Where the time went" table in `<project>/celltune/logs/`."
- Suggested: "**If training is slow, check the log.** Each run's log (`<project>/celltune/logs/training-<timestamp>.log`, last 20 kept) ends with a "Where the time went" table showing how long each step took." (TrainingLogRecorder.java:43; PhaseTimer.java:110)

**41. Lines 1715 — VERBOSE (repeated) — Low**
- Current: "**The channel mapping persists per project.** It's saved to … no re-import needed. Each project keeps its own; …"
- Suggested: "**Channel mapping is saved per project** in `celltune/marker-table.json` and reloads when you reopen the project. To use it in another project, click *Export CSV…* in *Channel Mapping (Review Display)...* and import that CSV there."

**42. Lines 1718 — UNCLEAR INSTRUCTIONS — Low**
- Current: "Tick "Prepend primary" and your existing multi-class palette is preserved."
- Problem: the checkbox is labelled differently.
- Suggested: "Tick **Prepend current primary classification (colour follows primary)** to keep your existing class colours." (CompositeClassificationDialog.java:44)

**43. Lines 1719 — VERBOSE — Low**
- Current: "this is deliberate (saving large slides is slow and pointless for navigation)."
- Suggested: "**Opening an image from Project Prediction Summary does not save the current image.** Save it yourself (Ctrl+S) before you switch if you have edited it."

Total: 43 (9 high, 19 medium, 15 low)
