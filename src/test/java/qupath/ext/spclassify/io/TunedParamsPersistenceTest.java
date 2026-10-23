package qupath.ext.spclassify.io;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.spclassify.classifier.DualModelClassifier;
import qupath.ext.spclassify.classifier.ModelType;
import qupath.ext.spclassify.classifier.TunedParams;
import qupath.lib.projects.Project;

class TunedParamsPersistenceTest {

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

    private static TunedParams xgb() {
        return new TunedParams("XGBOOST", 127, 4, 0.0731f, 0.75f, 0.6f, 0.9123, 20, 5, 812, 40, 6, "20261023_150000");
    }

    private static TunedParams lgb() {
        return new TunedParams("LIGHTGBM", 140, 7, 0.042f, 0.9f, 0.8f, 0.8871, 20, 5, 812, 40, 6, "20261023_150000");
    }

    @Test
    void savedIntoMultiClassAndBinaryStates() throws Exception {
        var project = project();
        Path ct = ProjectStateManager.getCellTuneDir(project);
        var multi = new ProjectStateManager.SavedState();
        multi.name = "CellTune";
        ProjectStateManager.writeState(ct.resolve("classifier-state.json"), multi);
        Files.createDirectories(ct.resolve("binary"));
        var binary = new ProjectStateManager.SavedState();
        binary.name = "CD3";
        ProjectStateManager.writeState(ct.resolve("binary").resolve("CD3.json"), binary);

        assertTrue(ProjectStateManager.saveTunedParams(project, null, List.of(xgb(), lgb())));
        assertTrue(ProjectStateManager.saveTunedParams(project, "CD3", List.of(xgb())));
        assertFalse(ProjectStateManager.saveTunedParams(project, "CD8", List.of(xgb())), "no state file");

        var loaded = ProjectStateManager.loadState(project);
        assertEquals(List.of(xgb(), lgb()), loaded.tunedParams);
        assertEquals("CellTune", loaded.name, "other fields kept");
        assertEquals(List.of(xgb()), ProjectStateManager.loadBinaryState(project, "CD3").tunedParams);

        assertTrue(ProjectStateManager.saveTunedParams(project, null, List.of()));
        assertNull(ProjectStateManager.loadState(project).tunedParams, "empty list clears the record");
    }

    @Test
    void olderStateLoadsWithNoTunedParams() throws Exception {
        var project = project();
        Path ct = ProjectStateManager.getCellTuneDir(project);
        Files.writeString(ct.resolve("classifier-state.json"), "{\"name\":\"CellTune\"}");

        assertNull(ProjectStateManager.loadState(project).tunedParams);
    }

    @Test
    void classifierKeysByModelTypeAndSkipsUnknown() {
        var classifier = new DualModelClassifier();
        var unknown = new TunedParams("CATBOOST", 1, 1, 0.1f, 1f, 1f, 0.5, 5, 2, 10, 1, 2, "x");
        classifier.setTunedParams(List.of(lgb(), unknown, xgb()));

        assertEquals(xgb(), classifier.getTunedParams(ModelType.XGBOOST));
        assertEquals(lgb(), classifier.getTunedParams(ModelType.LIGHTGBM));
        assertNull(classifier.getTunedParams(ModelType.RANDOM_FOREST));
        assertEquals(List.of(xgb(), lgb()), classifier.getTunedParams(), "ModelType order");

        classifier.setTunedParams(null);
        assertTrue(classifier.getTunedParams().isEmpty());
    }
}
