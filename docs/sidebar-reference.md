# Reference: every setting in the sidebar

| Control | Default | What it does |
|---|---|---|
| **Rounds** | 500 | Maximum boosting rounds (50–1000). With **Early stopping** ticked, training stops sooner when the model stops improving, so a high value costs little. With it unticked, every round is used; lower the value. |
| **Max depth** | 6 | Maximum tree depth (2–15). Higher values model more complex marker combinations but overfit more easily. |
| **CPU threads** | 0 (all) | Number of CPU threads training uses; 0 = all cores. Lower it to keep QuPath responsive during training. Remembered between sessions. See §5.3. |
| **Images at once** | 1 | Number of images classified at the same time by **Apply to which images...** after training (1–8). Each image is loaded whole, so higher values use more memory. Does not affect training. |
| **Model 1** | XGBoost | First model type. |
| **Model 2** | LightGBM | Second model type. Use a different type from Model 1, so the two models disagree on uncertain cells. |
| **Pool labels from all images** | ✅ | Train on labelled cells from every project image. Always on in binary mode. |
| **Enable data balancing** | ✅ | Resample the training set to balance the classes. Untick to hide **Strategy**. |
| **Strategy** | SMOTE + Tomek | Resampling method — see the §5.4 table. |
| **Auto-tune hyperparameters** | ❌ | Searches for better model settings. Cost = 2 × Trials × CV folds model fits (200 at the defaults). This can take several hours on a panel with many features. |
| **Trials** | 20 | Settings combinations tried per model (5–100). Shown only when Auto-tune is ticked. |
| **CV folds** | 5 | Cross-validation folds used to score each combination (2–10). Shown only when Auto-tune is ticked. |
| **Early stopping** | ✅ | Stops adding rounds when the score on held-out cells has not improved for 20 rounds. |
| **Train/val metrics** | ✅ | Produces the **Training Metrics** report. Adds about a third to training time. Untick to train faster without the report. |
| **Show top 10 feature importance after training** | ✅ | Opens the SHAP feature-importance plot after training. |
| **Auto-prune features (drop near-constant & redundant)** | ✅ | Before training, removes features that are almost constant or highly correlated with another feature of the same marker. The 5 highest-variance features of each marker are always kept. Runs only when more than 20 features are selected. Image measurements are not changed. See §[4.1](setup.md#41-select-features). |
| **Restrict to features shared with imported data** | ❌ | Train only on features that also exist in the imported ground-truth columns (names matched ignoring case). |
| **Sample current image only** | ❌ | Sample and review cells from the open image only. |
| **Filter by annotation keywords** | (blank) | Comma-separated keywords. Only cells inside annotations whose names contain a keyword (any case) are sampled for review. |
| **Apply to which images...** | (all) | Choose the project images the trained classifier is applied to. The button label shows how many are selected. |
| **Manual Label Mode** | — | Opens the floating labelling toolbar. |
| **Train** | — | Starts training. Needs at least 10 labelled cells. |
| **Agreement Confusion Matrix** | (disabled) | Shows how often the two models agree, per class. Available after training. |
| **Training Metrics** | (disabled) | Per-class precision, recall and F1 on a 20% held-out split. Available after training with **Train/val metrics** ticked and at least 20 labelled cells. |
| **Feature Importance...** | (disabled) | SHAP top features per class. Available after training. |
| **Enter Review Mode** | (disabled) | Samples cells where the two models disagree, for you to review. Available once predictions exist. |

### 14.1 Reference: preferences

Under **Edit → Preferences → SP Classify**.

| Preference | Default | What it does |
|---|---|---|
| **Enable SP Classify extension** | ✅ | Turns the extension off without uninstalling it. |
| **XGBoost histogram bins** | 0 | Leave at 0. See below. |
| **Use batch-corrected values** | ❌ | The same setting as **Use batch-corrected values in clustering + ML (streamed, no columns)** in the Batch Normalisation dialog. See §[19.4](batch-normalisation.md#194-how-its-applied). |

**XGBoost histogram bins.** Leave this at 0 (= 256 bins, the most accurate setting). Lower values make XGBoost training faster (128 about 2×, 64 about 2.5×) but change some predictions. To try a lower value, train once at 0 and once at 128, export the cell table each time, and compare the Training Metrics and the class columns. The training log records the value used as `XGB max_bin`.

---
