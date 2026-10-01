package qupath.ext.spclassify.io;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.ext.spclassify.model.CellTypeTable;
import qupath.lib.projects.Project;

class ProjectStateManagerMarkerTableTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsSimpleFormatRoundTrip() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("simple-project/project.qpproj"));

        CellTypeTable original = new CellTypeTable();
        original.put("T-Cell", List.of("CD3"));
        original.put("Macrophage", List.of("CD68", "CD163"));

        ProjectStateManager.saveMarkerTable(project, original);
        CellTypeTable loaded = ProjectStateManager.loadMarkerTable(project);

        assertNotNull(loaded);
        assertFalse(loaded.hasGatingRules());
        assertEquals(original.getCellTypes(), loaded.getCellTypes());
        assertEquals(List.of("CD3"), loaded.getMarkers("T-Cell"));
        assertEquals(List.of("CD68", "CD163"), loaded.getMarkers("Macrophage"));
    }

    @Test
    void savesAndLoadsRuleFormatRoundTrip() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("rule-project/project.qpproj"));

        CellTypeTable original = new CellTypeTable();
        original.putRule("CD8T", "CD8", "CD3", "CD103|CD45|CD45RA");
        original.putRule("Plasma_CD38", "CD38&!IgA", null, "CD45|VIM");

        ProjectStateManager.saveMarkerTable(project, original);
        CellTypeTable loaded = ProjectStateManager.loadMarkerTable(project);

        assertNotNull(loaded);
        assertTrue(loaded.hasGatingRules());
        assertEquals(original.getCellTypes(), loaded.getCellTypes());

        assertEquals("CD8", loaded.getPrimaryExpression("CD8T"));
        assertEquals("CD3", loaded.getSecondaryMarkers("CD8T"));
        assertEquals("CD103|CD45|CD45RA", loaded.getTertiaryMarkers("CD8T"));

        assertEquals("CD38&!IgA", loaded.getPrimaryExpression("Plasma_CD38"));
        assertNull(loaded.getSecondaryMarkers("Plasma_CD38"));
        assertEquals("CD45|VIM", loaded.getTertiaryMarkers("Plasma_CD38"));
    }

    @Test
    void returnsNullWhenMarkerTableDoesNotExist() {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("empty-project/project.qpproj"));
        assertNull(ProjectStateManager.loadMarkerTable(project));
    }

    @Test
    void savingNullOrEmptyClearsExistingFile() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("clear-project/project.qpproj"));

        CellTypeTable original = new CellTypeTable();
        original.put("T-Cell", List.of("CD3"));
        ProjectStateManager.saveMarkerTable(project, original);
        assertNotNull(ProjectStateManager.loadMarkerTable(project));

        ProjectStateManager.saveMarkerTable(project, null);
        assertNull(ProjectStateManager.loadMarkerTable(project));

        ProjectStateManager.saveMarkerTable(project, original);
        assertNotNull(ProjectStateManager.loadMarkerTable(project));

        ProjectStateManager.saveMarkerTable(project, new CellTypeTable());
        assertNull(ProjectStateManager.loadMarkerTable(project));
    }

    @Test
    void exactChannelsRoundTripUncappedAndSimpleMarkersStayReadable() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("v2-simple/project.qpproj"));
        List<String> many = List.of("CD3", "CD4", "CD8", "CD45", "CD45RA", "CD103", "PD-1 (Opal 690)");
        CellTypeTable original = new CellTypeTable();
        original.putChannels("T", many);
        original.put("Legacy", List.of("CD20"));

        ProjectStateManager.saveMarkerTable(project, original);
        CellTypeTable loaded = ProjectStateManager.loadMarkerTable(project);

        assertEquals(many, loaded.getChannels("T"));
        assertEquals(many, loaded.getMarkers("T"));
        assertFalse(loaded.hasChannels("Legacy"));
        assertEquals(List.of("CD20"), loaded.getMarkers("Legacy"));

        // An older build only reads "markers": it must still find them alongside "channels".
        JsonObject root = readJson(project);
        assertEquals(2, root.get("version").getAsInt());
        JsonObject t = root.getAsJsonArray("entries").get(0).getAsJsonObject();
        assertEquals(7, t.getAsJsonArray("markers").size());
        assertEquals(7, t.getAsJsonArray("channels").size());
        assertFalse(root.getAsJsonArray("entries").get(1).getAsJsonObject().has("channels"));
    }

    @Test
    void ruleTableWithExactChannelsRoundTrips() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("v2-rule/project.qpproj"));
        CellTypeTable original = new CellTypeTable();
        original.putRule("CD8T", "CD8&CD3", "CD45", null);
        original.putChannels("CD8T", List.of("CD8", "CD3", "CD31"));
        original.putRule("Plasma", "CD38&!IgA", null, "VIM");

        ProjectStateManager.saveMarkerTable(project, original);
        CellTypeTable loaded = ProjectStateManager.loadMarkerTable(project);

        assertTrue(loaded.hasGatingRules());
        assertEquals("CD8&CD3", loaded.getPrimaryExpression("CD8T"));
        assertEquals("CD45", loaded.getSecondaryMarkers("CD8T"));
        assertEquals(List.of("CD8", "CD3", "CD31"), loaded.getChannels("CD8T"));
        assertEquals("VIM", loaded.getTertiaryMarkers("Plasma"));
        assertFalse(loaded.hasChannels("Plasma"));
    }

    @Test
    void versionOneFileStillLoads() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("v1/project.qpproj"));
        Path dir = ProjectStateManager.getCellTuneDir(project);
        Files.writeString(dir.resolve("marker-table.json"), """
                {"version": 1, "hasRules": false, "entries": [
                  {"cellType": "T-Cell", "markers": ["CD3", "CD4"]},
                  {"cellType": "Macrophage", "markers": ["CD68"]}
                ]}
                """);
        CellTypeTable loaded = ProjectStateManager.loadMarkerTable(project);
        assertNotNull(loaded);
        assertEquals(List.of("CD3", "CD4"), loaded.getMarkers("T-Cell"));
        assertFalse(loaded.hasAnyChannels());
    }

    @Test
    void newerSchemaVersionStillLoadsKnownFields() throws Exception {
        Project<BufferedImage> project = fakeProject(tempDir.resolve("v9/project.qpproj"));
        Path dir = ProjectStateManager.getCellTuneDir(project);
        Files.writeString(
                dir.resolve("marker-table.json"),
                "{\"version\": 9, \"entries\": [{\"cellType\": \"T\", \"markers\": [\"CD3\"], \"future\": 1}]}");
        assertEquals(
                List.of("CD3"), ProjectStateManager.loadMarkerTable(project).getMarkers("T"));
    }

    private static JsonObject readJson(Project<?> project) throws IOException {
        Path file = ProjectStateManager.getCellTuneDir(project).resolve("marker-table.json");
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    @SuppressWarnings("unchecked")
    private static Project<BufferedImage> fakeProject(Path projectFile) {
        try {
            Files.createDirectories(projectFile.getParent());
            if (!Files.exists(projectFile)) {
                Files.createFile(projectFile);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return (Project<BufferedImage>) Proxy.newProxyInstance(
                Project.class.getClassLoader(), new Class[] {Project.class}, (proxy, method, args) -> {
                    String name = method.getName();
                    return switch (name) {
                        case "getPath" -> projectFile;
                        case "toString" -> "FakeProject(" + projectFile + ")";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        case "close" -> null;
                        default ->
                            throw new UnsupportedOperationException("Method not implemented in fake project: " + name);
                    };
                });
    }
}
