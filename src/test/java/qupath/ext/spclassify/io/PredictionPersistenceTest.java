package qupath.ext.spclassify.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.spclassify.model.CellPrediction;
import qupath.ext.spclassify.model.PopulationSet;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Round-trip coverage for the per-image prediction persistence extracted into
 * {@link PredictionPersistence}. Exercised through the {@link ProjectStateManager}
 * facade so both the delegation and the underlying logic are tested.
 */
class PredictionPersistenceTest {

    @TempDir
    Path tempDir;

    private static final List<String> CLASSES = List.of("Tumour", "Stroma", "Immune");

    @Test
    void savesAndLoadsPredictionsRoundTrip() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("p/project.qpproj"), List.of());

        PopulationSet predAll = new PopulationSet("Pred_ALL");
        predAll.put(
                "cell-1",
                new CellPrediction(
                        "cell-1",
                        "Tumour",
                        "Tumour",
                        new float[] {0.8f, 0.1f, 0.1f},
                        new float[] {0.7f, 0.2f, 0.1f},
                        CLASSES));
        predAll.put(
                "cell-2",
                new CellPrediction(
                        "cell-2",
                        "Stroma",
                        "Immune",
                        new float[] {0.1f, 0.6f, 0.3f},
                        new float[] {0.2f, 0.3f, 0.5f},
                        CLASSES));

        Path written = ProjectStateManager.saveImagePredictions(project, null, "slide A.tif", predAll);
        assertNotNull(written);
        assertTrue(Files.exists(written));

        PopulationSet loaded = ProjectStateManager.loadImagePredictions(project, null, "slide A.tif");
        assertNotNull(loaded);
        assertEquals(2, loaded.size());

        Map<String, CellPrediction> all = loaded.getAll();
        CellPrediction c1 = all.get("cell-1");
        assertNotNull(c1);
        assertEquals("Tumour", c1.getModel1Label());
        assertEquals("Tumour", c1.getModel2Label());
        assertEquals(CLASSES, c1.getClassNames());
        assertArrayEqualsExact(new float[] {0.8f, 0.1f, 0.1f}, c1.getModel1Probs());
        assertArrayEqualsExact(new float[] {0.7f, 0.2f, 0.1f}, c1.getModel2Probs());

        CellPrediction c2 = all.get("cell-2");
        assertNotNull(c2);
        assertEquals("Stroma", c2.getModel1Label());
        assertEquals("Immune", c2.getModel2Label());
    }

    @Test
    void emptyPredictionSetWritesNothing() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("empty/project.qpproj"), List.of());
        assertNull(ProjectStateManager.saveImagePredictions(project, null, "img", new PopulationSet("Pred_ALL")));
        assertNull(ProjectStateManager.saveImagePredictions(project, null, "img", null));
    }

    @Test
    void loadReturnsNullWhenNoFile() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("none/project.qpproj"), List.of());
        assertNull(ProjectStateManager.loadImagePredictions(project, null, "never-saved"));
    }

    @Test
    void listsImagesWithPredictions() throws Exception {
        Project<BufferedImage> project =
                fakeProject(tempDir.resolve("list/project.qpproj"), List.of("with-preds.tif", "without-preds.tif"));

        PopulationSet predAll = new PopulationSet("Pred_ALL");
        predAll.put(
                "cell-1",
                new CellPrediction(
                        "cell-1",
                        "Tumour",
                        "Tumour",
                        new float[] {0.8f, 0.1f, 0.1f},
                        new float[] {0.7f, 0.2f, 0.1f},
                        CLASSES));
        ProjectStateManager.saveImagePredictions(project, null, "with-preds.tif", predAll);

        List<String> imagesWithPreds = ProjectStateManager.listImagesWithPredictions(project, null);
        assertEquals(List.of("with-preds.tif"), imagesWithPreds);
    }

    @Test
    void binaryAndMultiClassPredictionsAreStoredSeparately() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("scope/project.qpproj"), List.of("img.tif"));
        List<String> binary = List.of("CD3+", "CD3-");
        PopulationSet multi = single("cell-1", "Tumour", new float[] {0.8f, 0.1f, 0.1f}, CLASSES);
        PopulationSet cd3 = single("cell-1", "CD3+", new float[] {0.9f, 0.1f}, binary);

        ProjectStateManager.saveImagePredictions(project, null, "img.tif", multi);
        ProjectStateManager.saveImagePredictions(project, "CD3", "img.tif", cd3);

        assertEquals(
                CLASSES,
                ProjectStateManager.loadImagePredictions(project, null, "img.tif")
                        .get("cell-1")
                        .getClassNames());
        assertEquals(
                binary,
                ProjectStateManager.loadImagePredictions(project, "CD3", "img.tif")
                        .get("cell-1")
                        .getClassNames());
        assertNull(ProjectStateManager.loadImagePredictions(project, "CD8", "img.tif"));
        assertEquals(List.of("img.tif"), ProjectStateManager.listImagesWithPredictions(project, "CD3"));
        assertEquals(List.of(), ProjectStateManager.listImagesWithPredictions(project, "CD8"));
    }

    @Test
    void streamingCountsEqualLoadedCounts() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("c/project.qpproj"), List.of());
        PopulationSet predAll = new PopulationSet("Pred_ALL");
        var rng = new java.util.Random(5);
        for (int i = 0; i < 500; i++) {
            float[] p1 = {rng.nextFloat(), rng.nextFloat(), rng.nextFloat()};
            float[] p2 = {rng.nextFloat(), rng.nextFloat(), rng.nextFloat()};
            if (i % 50 == 0) {
                // A tie between classes: the first index must win, as in avgLabel().
                p1 = new float[] {0.4f, 0.4f, 0.2f};
                p2 = new float[] {0.4f, 0.4f, 0.2f};
            }
            String l1 = CLASSES.get(rng.nextInt(3));
            String l2 = rng.nextInt(4) == 0 ? CLASSES.get(rng.nextInt(3)) : l1;
            predAll.put("cell-" + i, new CellPrediction("cell-" + i, l1, l2, p1, p2, CLASSES));
        }
        ProjectStateManager.saveImagePredictions(project, null, "img", predAll);

        var expected = qupath.ext.spclassify.model.PredictionCounts.of(
                ProjectStateManager.loadImagePredictions(project, null, "img"));
        var counted = PredictionPersistence.countImagePredictions(project, null, "img");
        assertEquals(expected, counted);
        assertEquals(
                List.copyOf(expected.avgCounts().keySet()),
                List.copyOf(counted.avgCounts().keySet()));
    }

    @Test
    void streamingCountsMatchLoaderOnInvalidAndRepeatedRows() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("d/project.qpproj"), List.of());
        Path file = PredictionPersistence.imagePredictionsFile(project, null, "img");
        Files.createDirectories(file.getParent());
        // Row 2: wrong probability length (skipped). Row 3: no cell ID (skipped).
        // Row 4 repeats cell a (replaces row 1, keeps its position). Row 5 has an unknown field.
        Files.writeString(file, """
                {"classNames": ["Tumour", "Stroma", "Immune"],
                 "predictions": [
                  {"cellId": "a", "model1Label": "Tumour", "model2Label": "Tumour",
                   "model1Probs": [0.9, 0.05, 0.05], "model2Probs": [0.8, 0.1, 0.1]},
                  {"cellId": "b", "model1Label": "Tumour", "model2Label": "Stroma",
                   "model1Probs": [0.9, 0.1], "model2Probs": [0.8, 0.1, 0.1]},
                  {"model1Label": "Tumour", "model2Label": "Stroma",
                   "model1Probs": [0.9, 0.05, 0.05], "model2Probs": [0.8, 0.1, 0.1]},
                  {"cellId": "c", "extra": {"x": [1, 2]}, "model1Label": "Immune", "model2Label": "Stroma",
                   "model1Probs": [0.1, 0.2, 0.7], "model2Probs": [0.1, 0.6, 0.3]},
                  {"cellId": "a", "model1Label": "Stroma", "model2Label": "Immune",
                   "model1Probs": [0.1, 0.6, 0.3], "model2Probs": [0.1, 0.3, 0.6]}
                 ]}
                """);
        var expected = qupath.ext.spclassify.model.PredictionCounts.of(
                ProjectStateManager.loadImagePredictions(project, null, "img"));
        var counted = PredictionPersistence.countImagePredictions(project, null, "img");
        assertEquals(expected, counted);
        assertEquals(2, counted.cells());
        assertEquals(2, counted.disagreements());
    }

    @Test
    void streamingCountsRejectTruncatedFile() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("e/project.qpproj"), List.of());
        Path file = PredictionPersistence.imagePredictionsFile(project, null, "img");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"classNames\": [\"A\", \"B\"], \"predictions\": [{\"cellId\": \"a\", \"mod");
        assertThrows(IOException.class, () -> PredictionPersistence.countImagePredictions(project, null, "img"));
        assertNull(PredictionPersistence.countImagePredictions(project, null, "missing"));
    }

    @Test
    void countsCacheReusesUnchangedFilesAndRecountsChangedOnes() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("f/project.qpproj"), List.of());
        PopulationSet one = single("cell-1", "Tumour", new float[] {0.8f, 0.1f, 0.1f}, CLASSES);
        ProjectStateManager.saveImagePredictions(project, null, "img", one);

        var cache = PredictionCountsCache.open(project);
        assertEquals(1, cache.get(null, "img").cells());
        cache.save();
        Path cacheFile = ProjectStateManager.cellTuneDirPath(project).resolve(PredictionCountsCache.FILENAME);
        assertTrue(Files.exists(cacheFile));

        // Reopened: same counts from the saved entry.
        assertEquals(1, PredictionCountsCache.open(project).get(null, "img").cells());

        // The predictions file changes (different size): counted again.
        PopulationSet two = single("cell-1", "Tumour", new float[] {0.8f, 0.1f, 0.1f}, CLASSES);
        two.put(
                "cell-2",
                new CellPrediction(
                        "cell-2",
                        "Stroma",
                        "Immune",
                        new float[] {0.1f, 0.6f, 0.3f},
                        new float[] {0.2f, 0.3f, 0.5f},
                        CLASSES));
        ProjectStateManager.saveImagePredictions(project, null, "img", two);
        var reopened = PredictionCountsCache.open(project);
        assertEquals(2, reopened.get(null, "img").cells());
        assertEquals(1, reopened.get(null, "img").disagreements());

        // Binary scope is a separate entry.
        assertNull(reopened.get("CD3", "img"));
    }

    @Test
    void predictionsMatchClassesComparesClassList() {
        PopulationSet preds = single("cell-1", "CD3+", new float[] {0.9f, 0.1f}, List.of("CD3+", "CD3-"));

        assertTrue(ProjectStateManager.predictionsMatchClasses(preds, List.of("CD3+", "CD3-")));
        assertFalse(ProjectStateManager.predictionsMatchClasses(preds, CLASSES));
        assertTrue(ProjectStateManager.predictionsMatchClasses(preds, null), "no classifier: no check");
        assertTrue(ProjectStateManager.predictionsMatchClasses(null, CLASSES));
    }

    private static PopulationSet single(String cellId, String label, float[] probs, List<String> classes) {
        PopulationSet set = new PopulationSet("Pred_ALL");
        set.put(cellId, new CellPrediction(cellId, label, label, probs, probs, classes));
        return set;
    }

    private static void assertArrayEqualsExact(float[] expected, float[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], 1e-6f, "index " + i);
        }
    }

    @SuppressWarnings("unchecked")
    private static Project<BufferedImage> fakeProject(Path projectFile, List<String> imageNames) {
        try {
            Files.createDirectories(projectFile.getParent());
            if (!Files.exists(projectFile)) {
                Files.createFile(projectFile);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        List<ProjectImageEntry<BufferedImage>> entries =
                imageNames.stream().map(PredictionPersistenceTest::fakeEntry).toList();

        return (Project<BufferedImage>) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class[] {Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPath" -> projectFile;
                    case "getImageList" -> entries;
                    case "toString" -> "FakeProject(" + projectFile + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "close" -> null;
                    default ->
                        throw new UnsupportedOperationException(
                                "Method not implemented in fake project: " + method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static ProjectImageEntry<BufferedImage> fakeEntry(String imageName) {
        return (ProjectImageEntry<BufferedImage>) Proxy.newProxyInstance(
                ProjectImageEntry.class.getClassLoader(),
                new Class[] {ProjectImageEntry.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getImageName" -> imageName;
                    case "toString" -> "FakeEntry(" + imageName + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default ->
                        throw new UnsupportedOperationException(
                                "Method not implemented in fake entry: " + method.getName());
                });
    }
}
