package qupath.ext.spclassify.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import qupath.lib.projects.Project;

class TileEntryCleanerTest {

    /** A Project whose getPath() returns the given .qpproj path; every other method returns null. */
    private static Project<?> projectAt(Path qpproj) {
        return (Project<?>) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class<?>[] {Project.class},
                (proxy, method, args) -> method.getName().equals("getPath") ? qpproj : null);
    }

    @Test
    void onlyDirectChildrenOfTheDataFolderMayBeDeleted() {
        Path base = Path.of("/projects/p1");
        var project = projectAt(base.resolve("project.qpproj"));

        assertTrue(TileEntryCleaner.isInProjectDataFolder(project, base.resolve("data/148")));

        assertFalse(TileEntryCleaner.isInProjectDataFolder(project, base.resolve("data")));
        assertFalse(TileEntryCleaner.isInProjectDataFolder(project, base));
        assertFalse(TileEntryCleaner.isInProjectDataFolder(project, base.resolve("data/148/sub")));
        assertFalse(TileEntryCleaner.isInProjectDataFolder(project, base.resolve("data/../classifiers")));
        assertFalse(TileEntryCleaner.isInProjectDataFolder(project, Path.of("/projects/p2/data/148")));
    }

    @Test
    void noProjectPathMeansNothingIsDeleted() {
        assertFalse(TileEntryCleaner.isInProjectDataFolder(projectAt(null), Path.of("/projects/p1/data/148")));
    }
}
