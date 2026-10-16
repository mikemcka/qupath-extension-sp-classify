package qupath.ext.spclassify.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.spclassify.io.ProjectStateManager;
import qupath.ext.spclassify.model.LabelStore;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

class TrainingOrchestratorTest {

    @TempDir
    Path tempDir;

    @Test
    void countsSavedLabelsOnOtherImagesOnly() throws Exception {
        var open = fakeEntry("open.tif");
        var other = fakeEntry("other.tif");
        Project<BufferedImage> project = fakeProject(tempDir.resolve("project.qpproj"), List.of(open, other), open);

        ProjectStateManager.saveImageLabels(
                project, null, "open.tif", new LabelStore("t", Map.of("a", "Tcell", "b", "Tcell")));
        ProjectStateManager.saveImageLabels(
                project,
                null,
                "other.tif",
                new LabelStore("t", Map.of("c", "Tcell", "d", "Bcell", "e", "CD4-mergedInto(Tcell)", "f", "Debris")));

        assertEquals(4, TrainingOrchestrator.countLabelsOnOtherImages(project, null, null, null));
        assertEquals(
                3,
                TrainingOrchestrator.countLabelsOnOtherImages(project, null, null, Set.of("Tcell", "Bcell")),
                "class filter matches the effective (post-merge) class and drops Debris");
        assertEquals(0, TrainingOrchestrator.countLabelsOnOtherImages(project, null, "CD3", null), "other scope");
    }

    @SuppressWarnings("unchecked")
    private static Project<BufferedImage> fakeProject(
            Path projectFile, List<ProjectImageEntry<BufferedImage>> entries, ProjectImageEntry<BufferedImage> open)
            throws Exception {
        Files.createDirectories(projectFile.getParent());
        Files.createFile(projectFile);
        return (Project<BufferedImage>) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class[] {Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPath" -> projectFile;
                    case "getImageList" -> entries;
                    case "getEntry" -> open;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static ProjectImageEntry<BufferedImage> fakeEntry(String imageName) {
        return (ProjectImageEntry<BufferedImage>) Proxy.newProxyInstance(
                ProjectImageEntry.class.getClassLoader(),
                new Class[] {ProjectImageEntry.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getImageName" -> imageName;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
