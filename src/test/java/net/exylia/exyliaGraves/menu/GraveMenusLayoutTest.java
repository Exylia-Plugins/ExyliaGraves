package net.exylia.exyliaGraves.menu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code %grave_actions%} spans several lore lines, and a single-line value written beside
 * it is repeated on each of them: the action line has to stand alone in every language.
 */
class GraveMenusLayoutTest {

    @Test
    @DisplayName("the action prompts sit on a lore line of their own")
    void actionsStandAlone() throws Exception {
        List<Path> menus;
        try (Stream<Path> files = Files.walk(Path.of("src/main/resources/lang"))) {
            menus = files.filter(path -> path.getFileName().toString().equals("graves.yml")).toList();
        }
        assertFalse(menus.isEmpty(), "no graves menus found");
        for (Path menu : menus) {
            List<String> lines = Files.readAllLines(menu).stream()
                    .filter(line -> line.contains("%grave_actions%") && !line.trim().startsWith("#"))
                    .toList();
            assertEquals(List.of("      - \"%grave_actions%\""), lines, menu.toString());
        }
    }
}
