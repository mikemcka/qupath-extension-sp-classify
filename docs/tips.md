# Tips, tricks and known limitations

- **Label at least 20–30 cells per class** before the first Train. Then use Review Mode to add labels where the two models disagree.
- **Select several cells at once:** hold Ctrl (Windows/Linux) or Cmd (Mac) while clicking cells, then apply a label.
- **Channel viewer:** *View → Channel viewer* shows the area under the cursor in every selected channel side by side.
- **Run Resolve Hierarchy before exporting.** Without it, the `ParentAnnotations` column can be empty for some cells in exports; `ContainingAnnotations` still lists them. Run *Utility Scripts → Resolve Hierarchy...* first (§[13.2](utility-scripts.md#132-resolve-hierarchy)).
- **Training Metrics overestimate accuracy on new images.** The 20% held-out cells come from the same images used for training. Check predictions on a few images with no labels before relying on the scores.
- **Use different model types for Model 1 and Model 2.** Two models of the same type rarely disagree, so Review Mode finds few cells to review.
- **Images at once is limited to 8.** Each image needs about 2–4 GB of memory on COMET data. This setting is separate from **CPU threads** — see §5.3.
- **If training is slow, check the log.** Each run's log (`<project>/celltune/logs/training-<timestamp>.log`, last 20 kept) ends with a "Where the time went" table showing how long each step took.
- **Channel mapping is saved per project** in `celltune/marker-table.json` and reloads when you reopen the project. To use it in another project, click **Export CSV…** in the [Channel Mapping editor](marker-table.md) and import that CSV there. *Reset Project State* deletes it with the rest of `celltune/`.
- **Fixing a CSV that picks the wrong channels.** Open *Channel Mapping (Review Display)...*, click **Import CSV…**, find rows marked `~`, `!` or `✗`, correct the ticks, click **Pin all matches**, then **Save**. Details in [Marker table format](marker-table.md#fixing-a-csv-that-picks-the-wrong-channels).
- **Project Prediction Summary needs at least 5 images** for reliable robust z-scores. With 2–3 images, use the Anomaly column only as a rough guide.
- **Composite class colours.** Without **Prepend current primary classification (colour follows primary)**, QuPath creates a colour for each composite name, which can mean hundreds of colours. Tick it to keep your existing class colours.
- **Opening an image from Project Prediction Summary does not save the current image.** Save it yourself (Ctrl+S) before you switch if you have edited it.

---

*Found a missing step or a wrong label? Open an issue on the GitHub repo. Screenshots welcome.*
