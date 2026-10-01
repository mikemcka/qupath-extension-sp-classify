# Multi-class workflow in detail

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

> **A high F1 score does not mean the classifier works on new images.** The validation cells come from the same images as the training cells, so F1 overestimates performance on other images. To check, open a different image, apply the classifier, look at the results, and check the Project Prediction Summary (§[8](prediction-summary.md)). If predictions on an image look wrong, label some of its cells and retrain.

#### Feature Importance (button)

**Feature Importance...** shows the 10 features that most affect each class's prediction (mean |SHAP| value), as horizontal bars. Use the **Class:** dropdown to switch class. Values come from the XGBoost model and from any Random Forest model. LightGBM is not included, so with the default pair (XGBoost + LightGBM) the chart shows XGBoost only.

Use it to find features to remove:

- If one feature (e.g. `Cell: DAPI Mean`) ranks highest for every class, de-select it in Select Features (§[4.1](setup.md#41-select-features)) and retrain.
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
