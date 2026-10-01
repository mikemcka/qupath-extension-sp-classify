# Binary + composite workflow in detail

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
