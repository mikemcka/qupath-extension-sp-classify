# Quick start — binary + composite workflow

Build one **positive/negative classifier per marker** (CD3, CD4, CD8, CD20…), then combine them into composite cell types (`CD3+:CD4+:CD8-`, etc.).

```
Select Features  →  Clustering Normalisation
       ↓
Extensions → SP Classify → Binary Classifiers... → Create... (name it CD3)
→ Open (enters Binary Mode)
       ↓
Manual Label Mode → label CD3-positive vs CD3-negative cells
       ↓
Apply to which images... → Train → Review Mode (correct disagreements)
       ↓
Exit Binary Mode → repeat for CD4, CD8, CD20, etc.
       ↓
Composite Classification... → tick markers, tick images
       ↓
(optional) tick "Prepend current primary classification (colour follows
primary)" so each cell keeps its multi-class class name and colour in
front of the marker labels
       ↓
Apply → composite labels appear in viewer (Tumour:CD3+:CD8-, …)
       ↓
Export Cell Table
```

Each step is described in §[6](binary-composite.md).

---
