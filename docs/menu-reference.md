# Reference: every SP Classify menu item

All under *Extensions → SP Classify*, in menu order.

| Item | Requires | Action |
|---|---|---|
| Binary Classifiers... | Project | Opens the binary classifier manager: create, open or delete one classifier per marker. |
| Composite Classification... | Project + ≥1 trained binary classifier | Applies trained binary classifiers and assigns composite labels. |
| Class Control... | Project | Add, delete, merge and undo-merge classes. |
| Channel Mapping (Review Display)... | Open image | Sets which image channels review shows for each class. Saved per project, with CSV import and export. See [Marker table format](marker-table.md). |
| Select Features... | Project | Choose which measurement columns are used for training. |
| Clustering Normalisation | Project | Sets an arcsinh or square-root transform per feature, with a shared cofactor, for clustering only. The classifier uses raw values. See §[4.2](setup.md#42-clustering-normalisation). |
| Project Prediction Summary... | Project | Cohort QC: anomaly score and flags for each image. See §[8](prediction-summary.md). |
| Intensity Heatmaps... | Open image with detections | Heatmap of mean marker intensity per class (z-score colours), for one image or the whole project. PNG and CSV export. See §[9](intensity-heatmaps.md). |
| Image Pixel Prescreen... | Project | Whole-image QC without cells: per-channel pixel statistics compared across the project, with flags for background-heavy, saturated, weak-signal and intensity-outlier images. CSV export. See §[17](pixel-prescreen.md). |
| Scatter Plots and Clustering... | Open image with detections | PCA/UMAP scatter plot with k-means or Leiden clustering, annotation and class gating, and cluster → class assignment. **Scope** switches to clustering across several images. See §[11](scatter-clustering.md). |
| Cellular Neighborhoods... | Open image with cells in at least 2 classes | Groups cells by the cell types around them (k-means on neighbourhood composition), for one image or the whole project. See §[18](neighborhoods.md). |
| Batch Normalisation... | Project | Aligns each marker's intensity across images (UniFORM). Fit and QC, then use the corrected values in clustering and the classifier, or write them as `(batchnorm)` columns. See §[19](batch-normalisation.md). |
| Generate Distance Measurements... | Project | Distances to annotations (signed), to other classes, and to the nearest cell of the same class, for the selected images. See §[10](distance-measurements.md). |
| Export → Cell Table... | Open image with detections | One CSV per selected image. See §[12.1](exporting.md#121-cell-table-export). |
| Export → Ground Truth... | Open image with labels (multi-class) | CSV of labels and feature values that you can import into another project. |
| Export → Active Binary Ground Truth... | Binary mode active + open image with labels | As above, for the active marker only. |
| Import → Marker Table... | Open image | Loads a cell type → markers CSV used to switch channels during review. The [Channel Mapping editor](marker-table.md) is the recommended way to edit it. |
| Import → Ground Truth... | Open image (multi-class) | Imports labels by spatial match, or as training data only. |
| Import → Active Binary Ground Truth... | Binary mode active + open image | As above, for the active marker only. |
| Utility Scripts → Filter Cells by Size & Circularity... | Open image with cells | Removes cells outside optional area and circularity limits, in the current image. See §[13.1](utility-scripts.md#131-filter-cells-by-size--circularity). |
| Utility Scripts → Resolve Hierarchy... | Open image or project | Rebuilds parent/child relationships between objects, for the current image or the whole project. See §[13.2](utility-scripts.md#132-resolve-hierarchy). |
| Utility Scripts → Lock All Annotations | Open image or project | Locks every annotation, in the current image or in all project images, so it cannot be moved or edited by accident. See §[13.7](utility-scripts.md#137-lock-all-annotations). |
| Utility Scripts → [TEST] Import GeoJSON Objects... | Open image | Imports objects from a GeoJSON or gzipped GeoJSON file into the current image. Small to medium files only. See §[13.4](utility-scripts.md#134-import-geojson-objects). |
| Utility Scripts → [TEST] Export Annotation Regions... | Open image | Exports annotation regions from the current image as polygon-masked OME-TIFF files. One image, small to medium regions only. See §[13.5](utility-scripts.md#135-export-annotation-regions). |
| Utility Scripts → Delete Measurements by Keyword... | Open image or project | **Destructive:** deletes detection measurements whose names contain a keyword. Shows the matches and asks you to confirm. See §[13.3](utility-scripts.md#133-delete-measurements-by-keyword). |
| Utility Scripts → Reset Project State... | Project | **Destructive:** deletes the project's `celltune/` folder (labels, models, predictions, settings). Writes a backup zip first and asks you to type `RESET`. Can also clear SP Classify label points and cell classifications from every image. See §[13.6](utility-scripts.md#136-reset-project-state). |
| How to Cite... | Nothing (always available) | Shows the references to cite for SP Classify and for the methods you used. |

---
