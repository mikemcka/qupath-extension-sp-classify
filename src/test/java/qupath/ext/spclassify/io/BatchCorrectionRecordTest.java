package qupath.ext.spclassify.io;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.lib.projects.Project;

class BatchCorrectionRecordTest {

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

    @Test
    void recordIsMergedIntoMultiClassAndBinaryStates() throws Exception {
        var project = project();
        Path ct = ProjectStateManager.getCellTuneDir(project);
        var multi = new ProjectStateManager.SavedState();
        multi.name = "CellTune";
        ProjectStateManager.writeState(ct.resolve("classifier-state.json"), multi);
        Files.createDirectories(ct.resolve("binary"));
        var binary = new ProjectStateManager.SavedState();
        binary.name = "CD3";
        ProjectStateManager.writeState(ct.resolve("binary").resolve("CD3.json"), binary);

        assertTrue(ProjectStateManager.saveBatchCorrectionRecord(project, null, true, "abc123"));
        assertTrue(ProjectStateManager.saveBatchCorrectionRecord(project, "CD3", false, null));
        assertFalse(ProjectStateManager.saveBatchCorrectionRecord(project, "CD8", true, "x"), "no state file");

        var loaded = ProjectStateManager.loadState(project);
        assertEquals(Boolean.TRUE, loaded.batchCorrectionUsed);
        assertEquals("abc123", loaded.batchShiftsFingerprint);
        assertEquals("CellTune", loaded.name, "other fields kept");
        var loadedBinary = ProjectStateManager.loadBinaryState(project, "CD3");
        assertEquals(Boolean.FALSE, loadedBinary.batchCorrectionUsed);
    }

    @Test
    void olderStateLoadsWithUnknownRecord() throws Exception {
        var project = project();
        Path ct = ProjectStateManager.getCellTuneDir(project);
        Files.writeString(ct.resolve("classifier-state.json"), "{\"name\":\"CellTune\"}");

        var loaded = ProjectStateManager.loadState(project);
        assertNull(loaded.batchCorrectionUsed);
        assertNull(loaded.batchShiftsFingerprint);
    }
}
