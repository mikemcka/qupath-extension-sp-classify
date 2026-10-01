# Utility scripts

*Extensions → SP Classify → **Utility Scripts***

Tools for common cleanup tasks. Each one asks for its settings, then reports what it changed.

### 13.1 Filter Cells by Size & Circularity

Removes cell detections from the **current image** that fall outside size and shape limits. The dialog has a row for **Area** and a row for **Circularity (0–1)**. Each row has a **Measurement** dropdown and **Min** and **Max** boxes. The dropdowns list every measurement whose name contains "area" or "circularity" and start on the whole-cell measurement (`Cell: Area µm^2`, or `Cell: Area px^2` if the image is not calibrated, and `Cell: Circularity`). Change them to filter on, for example, nucleus area. Area limits are in the units of the chosen measurement. The dialog opens with Max area = 500 and Min circularity = 0.7. Clear a box to remove that limit. A cell is removed if it breaks any limit (e.g. `Cell: Area µm^2 > 500` **or** `Cell: Circularity < 0.7`). Cells missing a measurement that has a limit are kept. The number of cells to be removed is shown before anything is deleted.

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
