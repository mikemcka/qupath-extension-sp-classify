package qupath.ext.spclassify.io;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.spclassify.classifier.ModelType;
import qupath.ext.spclassify.model.LabelStore;
import qupath.lib.projects.Project;

class SelectedFeaturesPersistenceTest {

    @TempDir
    Path tempDir;

    @SuppressWarnings("unchecked")
    private Project<BufferedImage> project() throws Exception {
        Path file = tempDir.resolve("project.qpproj");
        Files.createFile(file);
        return (Project<BufferedImage>) Proxy.newProxyInstance(
                Project.class.getClassLoader(), new Class[] {Project.class}, (proxy, m, args) -> switch (m.getName()) {
                    case "getPath" -> file;
                    case "getImageList" -> List.of();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }

    private static void saveTrainedState(Project<?> project) throws Exception {
        ProjectStateManager.saveState(
                project,
                "CellTune",
                new LabelStore("CellTune"),
                List.of("CD3: Mean"),
                List.of("A", "B"),
                null,
                null,
                null,
                null,
                ModelType.XGBOOST,
                ModelType.LIGHTGBM);
    }

    @Test
    void trainingSaveKeepsSelectedFeatures() throws Exception {
        var project = project();
        ProjectStateManager.saveSelectedFeatures(project, List.of("CD3: Mean", "CD8: Mean"));

        saveTrainedState(project);

        assertEquals(List.of("CD3: Mean", "CD8: Mean"), ProjectStateManager.loadState(project).selectedFeatures);
    }

    @Test
    void clearedSelectionStaysCleared() throws Exception {
        var project = project();
        ProjectStateManager.saveSelectedFeatures(project, List.of("CD3: Mean"));
        ProjectStateManager.saveSelectedFeatures(project, null);

        saveTrainedState(project);

        assertNull(ProjectStateManager.loadState(project).selectedFeatures);
    }
}
