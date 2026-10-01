# Batch normalisation (UniFORM)

Staining intensity often differs between slides or runs. Batch normalisation multiplies each image's intensity for each marker by one correction factor, so that the distribution lines up with a reference. Only the position of the distribution changes, not its shape. Clustering and the classifier then see the same intensity scale across the cohort. The method is feature-level UniFORM (Wang et al., *Cell Reports Methods* 2025; see [README ▸ References](how-to-cite.md)).

Open it from *Extensions → SP Classify → Batch Normalisation...*.

> The correction factor for each channel is calculated from its **Cell: Mean** intensities (or **Cell: Median** if Mean is not selected) and applied to every selected measurement of that channel. Only intensity measurements are corrected; foundation-model embeddings are excluded. Existing measurements are never changed; corrected values are added as new columns only if you click **Write corrected columns** (§19.4).

### 19.1 When to use it

- You cluster or train across images stained in different runs, and clusters or classes follow the slide rather than the biology.
- Clustering normalisation (§[4.2](setup.md#42-clustering-normalisation)) applies the same transform to every image, so it cannot remove differences between slides. Batch normalisation corrects each image separately. You do not need it for a single image.

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
