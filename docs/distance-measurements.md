# Distance measurements (spatial analysis)

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
