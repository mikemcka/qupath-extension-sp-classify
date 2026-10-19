package qupath.ext.spclassify.io;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.ext.spclassify.model.CellPrediction;
import qupath.ext.spclassify.model.PopulationSet;
import qupath.ext.spclassify.model.PredictionCounts;
import qupath.lib.projects.Project;

/**
 * Per-image {@code Pred_ALL} prediction persistence.
 * <p>
 * Multi-class predictions are stored as {@code <project>/celltune/image-predictions/<image>.json};
 * a binary classifier's are stored as
 * {@code <project>/celltune/binary-image-predictions/<scope>/<image>.json}, so binary and
 * multi-class predictions never overwrite each other. Each file records the class-name ordering plus one entry per cell
 * (both model labels and their probability vectors).
 * <p>
 * Extracted verbatim from {@link ProjectStateManager}; the public API there delegates
 * here so existing call sites are unaffected. Shares
 * {@link ProjectStateManager#getCellTuneDir(Project)}, {@link ProjectStateManager#sanitiseFileName(String)}
 * and {@link ProjectStateManager#GSON} with its siblings in this package.
 */
final class PredictionPersistence {

    private static final Logger logger = LoggerFactory.getLogger(PredictionPersistence.class);
    private static final String IMAGE_PREDICTIONS_DIR = "image-predictions";
    private static final String BINARY_IMAGE_PREDICTIONS_DIR = "binary-image-predictions";

    private PredictionPersistence() {} // utility class

    /** {@code scope} null/blank = multi-class; otherwise a binary marker name, as for labels. */
    private static Path resolvePredictionsDir(Project<?> project, String scope) throws IOException {
        Path ctDir = ProjectStateManager.getCellTuneDir(project);
        if (scope == null || scope.isBlank()) {
            return ctDir.resolve(IMAGE_PREDICTIONS_DIR);
        }
        return ctDir.resolve(BINARY_IMAGE_PREDICTIONS_DIR).resolve(ProjectStateManager.sanitiseFileName(scope));
    }

    /** Internal JSON shape for persisted per-image predictions. */
    private static class SavedPredictions {
        List<String> classNames;
        List<SavedPredictionEntry> predictions;
    }

    /** Internal JSON shape for a single persisted cell prediction. */
    private static class SavedPredictionEntry {
        String cellId;
        String model1Label;
        String model2Label;
        float[] model1Probs;
        float[] model2Probs;
    }

    /**
     * Save Pred_ALL predictions for a specific image.
     */
    static Path saveImagePredictions(Project<?> project, String scope, String imageName, PopulationSet predAll)
            throws IOException {
        if (predAll == null || predAll.size() == 0) {
            return null;
        }

        Path dir = resolvePredictionsDir(project, scope);
        Files.createDirectories(dir);

        String safeFileName = ProjectStateManager.sanitiseFileName(imageName) + ".json";
        Path outPath = dir.resolve(safeFileName);

        SavedPredictions state = new SavedPredictions();
        state.predictions = new ArrayList<>(predAll.size());

        List<String> classNames = null;
        for (CellPrediction pred : predAll.getAll().values()) {
            if (classNames == null || classNames.isEmpty()) {
                classNames = pred.getClassNames();
            }

            SavedPredictionEntry row = new SavedPredictionEntry();
            row.cellId = pred.getCellId();
            row.model1Label = pred.getModel1Label();
            row.model2Label = pred.getModel2Label();
            row.model1Probs = pred.getModel1Probs();
            row.model2Probs = pred.getModel2Probs();
            state.predictions.add(row);
        }

        if (classNames == null || classNames.isEmpty() || state.predictions.isEmpty()) {
            return null;
        }

        state.classNames = List.copyOf(classNames);
        Files.writeString(outPath, ProjectStateManager.GSON.toJson(state), StandardCharsets.UTF_8);
        logger.info("Saved {} predictions for image '{}' to {}", state.predictions.size(), imageName, outPath);
        return outPath;
    }

    /**
     * Load Pred_ALL predictions previously saved for a specific image.
     */
    static PopulationSet loadImagePredictions(Project<?> project, String scope, String imageName) throws IOException {
        return loadPredictionsFile(imagePredictionsFile(project, scope, imageName), imageName);
    }

    private static PopulationSet loadPredictionsFile(Path filePath, String imageName) throws IOException {
        if (!Files.exists(filePath)) {
            return null;
        }

        SavedPredictions saved = ProjectStateManager.GSON.fromJson(
                Files.readString(filePath, StandardCharsets.UTF_8), SavedPredictions.class);
        if (saved == null
                || saved.classNames == null
                || saved.classNames.isEmpty()
                || saved.predictions == null
                || saved.predictions.isEmpty()) {
            return null;
        }

        PopulationSet predAll = new PopulationSet("Pred_ALL");
        for (SavedPredictionEntry row : saved.predictions) {
            if (row == null
                    || row.cellId == null
                    || row.cellId.isBlank()
                    || row.model1Label == null
                    || row.model2Label == null
                    || row.model1Probs == null
                    || row.model2Probs == null) {
                continue;
            }

            if (row.model1Probs.length != saved.classNames.size()
                    || row.model2Probs.length != saved.classNames.size()) {
                continue;
            }

            CellPrediction pred = new CellPrediction(
                    row.cellId, row.model1Label, row.model2Label, row.model1Probs, row.model2Probs, saved.classNames);
            predAll.put(row.cellId, pred);
        }

        if (predAll.size() == 0) {
            return null;
        }

        logger.info("Loaded {} predictions for image '{}' from {}", predAll.size(), imageName, filePath);
        return predAll;
    }

    /** @return the file an image's predictions are saved to (it may not exist) */
    static Path imagePredictionsFile(Project<?> project, String scope, String imageName) throws IOException {
        return resolvePredictionsDir(project, scope).resolve(ProjectStateManager.sanitiseFileName(imageName) + ".json");
    }

    /**
     * Count an image's saved predictions without loading them: the file is read as a
     * stream and only the counts are kept. Rows are validated exactly as
     * {@link #loadImagePredictions} does, and a repeated cell ID replaces the earlier
     * row, so the result equals {@link PredictionCounts#of} on the loaded predictions.
     *
     * @return the counts, or {@code null} when there is no file or no valid prediction
     * @throws IOException if the file cannot be read or is not valid JSON
     */
    static PredictionCounts countImagePredictions(Project<?> project, String scope, String imageName)
            throws IOException {
        return countPredictionsFile(imagePredictionsFile(project, scope, imageName));
    }

    static PredictionCounts countPredictionsFile(Path filePath) throws IOException {
        if (!Files.exists(filePath)) {
            return null;
        }
        List<String> classNames = null;
        // cellId → avg class index × 2 + (1 if the models disagree); keeps first-insertion
        // order and last-row-wins like the PopulationSet the loader builds.
        var codes = new LinkedHashMap<String, Integer>();
        try (var reader = new JsonReader(Files.newBufferedReader(filePath, StandardCharsets.UTF_8))) {
            // Accept what Gson.fromJson accepts (e.g. NaN), so both readers agree on every file.
            reader.setStrictness(com.google.gson.Strictness.LEGACY_STRICT);
            reader.beginObject();
            while (reader.hasNext()) {
                String field = reader.nextName();
                if ("classNames".equals(field) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    classNames = new ArrayList<>();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        classNames.add(reader.peek() == JsonToken.NULL ? nullValue(reader) : reader.nextString());
                    }
                    reader.endArray();
                } else if ("predictions".equals(field) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    if (classNames == null) {
                        // Not the layout this class writes; count the slow way.
                        reader.close();
                        return PredictionCounts.of(loadPredictionsFile(filePath, "?"));
                    }
                    reader.beginArray();
                    while (reader.hasNext()) {
                        readRow(reader, classNames.size(), codes);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (IllegalStateException | NumberFormatException | com.google.gson.JsonParseException e) {
            throw new IOException("Invalid predictions file " + filePath.getFileName() + ": " + e.getMessage(), e);
        }
        if (classNames == null || classNames.isEmpty() || codes.isEmpty()) {
            return null;
        }
        long disagreements = 0;
        var avgCounts = new LinkedHashMap<String, Long>();
        for (int code : codes.values()) {
            disagreements += code & 1;
            avgCounts.merge(classNames.get(code >> 1), 1L, Long::sum);
        }
        return new PredictionCounts(classNames, codes.size(), disagreements, avgCounts);
    }

    private static String nullValue(JsonReader reader) throws IOException {
        reader.nextNull();
        return null;
    }

    /** Read one prediction row; add its code if it passes the loader's validation. */
    private static void readRow(JsonReader reader, int nClasses, Map<String, Integer> codes) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            reader.skipValue();
            return;
        }
        String cellId = null;
        String label1 = null;
        String label2 = null;
        float[] probs1 = null;
        float[] probs2 = null;
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
                continue;
            }
            switch (name) {
                case "cellId" -> cellId = reader.nextString();
                case "model1Label" -> label1 = reader.nextString();
                case "model2Label" -> label2 = reader.nextString();
                case "model1Probs" -> probs1 = readFloats(reader);
                case "model2Probs" -> probs2 = readFloats(reader);
                default -> reader.skipValue();
            }
        }
        reader.endObject();
        if (cellId == null
                || cellId.isBlank()
                || label1 == null
                || label2 == null
                || probs1 == null
                || probs2 == null
                || probs1.length != nClasses
                || probs2.length != nClasses) {
            return;
        }
        // Same arithmetic and tie-breaking as CellPrediction.avgLabel().
        int bestIdx = 0;
        float bestAvg = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < nClasses; i++) {
            float avg = (probs1[i] + probs2[i]) / 2f;
            if (avg > bestAvg) {
                bestAvg = avg;
                bestIdx = i;
            }
        }
        codes.put(cellId, bestIdx * 2 + (label1.equals(label2) ? 0 : 1));
    }

    private static float[] readFloats(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            reader.skipValue();
            return null;
        }
        float[] buf = new float[16];
        int n = 0;
        reader.beginArray();
        while (reader.hasNext()) {
            if (n == buf.length) {
                buf = java.util.Arrays.copyOf(buf, n * 2);
            }
            buf[n++] = (float) reader.nextDouble();
        }
        reader.endArray();
        return java.util.Arrays.copyOf(buf, n);
    }

    /**
     * List the names of all images in the project that have saved prediction files.
     */
    static List<String> listImagesWithPredictions(Project<?> project, String scope) throws IOException {
        Path dir = resolvePredictionsDir(project, scope);
        if (!Files.exists(dir)) return List.of();
        List<String> result = new ArrayList<>();
        for (var entry : project.getImageList()) {
            String name = entry.getImageName();
            if (Files.exists(dir.resolve(ProjectStateManager.sanitiseFileName(name) + ".json"))) {
                result.add(name);
            }
        }
        return result;
    }
}
