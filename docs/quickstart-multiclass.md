# Quick start — multi-class workflow

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

Each step is described in §[4](setup.md), §[5](multiclass.md) and §[7](review-mode.md).

---
