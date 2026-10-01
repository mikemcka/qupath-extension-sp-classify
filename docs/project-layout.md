# Project directory layout

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
