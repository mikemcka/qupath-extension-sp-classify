# Exporting results

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
