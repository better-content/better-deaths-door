package com.bettercontent.betterdeathsdoor;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JournalPanelCooperationTest {
    @Test void optionalHostOwnsPresentationOnly() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/bettercontent/betterdeathsdoor/client/ClientRevivalInput.java"));
        assertTrue(source.contains("journalPanelHost = screen -> false"));
        assertTrue(source.contains("if (journalPanelHost.test(screen)) return;"));
        assertFalse(source.contains("JournalInventoryScreen"));
        assertTrue(source.contains("openOwnBody()"));
        assertFalse(source.contains("setStackInSlot"));
        assertFalse(source.contains("setItem("));
    }
}
