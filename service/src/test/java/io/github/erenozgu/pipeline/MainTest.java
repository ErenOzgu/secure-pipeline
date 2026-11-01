package io.github.erenozgu.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void thePortDefaultsTo8080() {
        assertEquals(8080, Main.port(null));
        assertEquals(8080, Main.port("  "));
    }

    @Test
    void thePortIsReadFromTheValue() {
        assertEquals(9090, Main.port("9090"));
        assertEquals(1, Main.port("1"));
        assertEquals(65535, Main.port(" 65535 "));
    }

    @Test
    void aPortThatIsNotAPortIsRefusedWithAMessage() {
        for (String value : new String[] {"abc", "0", "65536", "-1", "80.5"}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Main.port(value));
            assertEquals(true, error.getMessage().contains("PORT"), value);
        }
    }
}
