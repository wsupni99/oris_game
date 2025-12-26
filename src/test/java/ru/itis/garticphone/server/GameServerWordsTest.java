package ru.itis.garticphone.server;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.*;

class GameServerWordsTest {

    @Test
    @SuppressWarnings("unchecked")
    void generateWordShouldUseWordsFromFile() throws Exception {
        ScheduledExecutorService roundScheduler = Executors.newScheduledThreadPool(1);
        GameService server = new GameService(roundScheduler);

        Field wordsField = GameService.class.getDeclaredField("words");
        wordsField.setAccessible(true);
        List<String> words = (List<String>) wordsField.get(server);

        assertNotNull(words);
        assertFalse(words.isEmpty());

        for (String w : words) {
            assertNotNull(w);
            assertFalse(w.isBlank());
        }

        String word = server.generateWord();
        assertNotNull(word);
        assertFalse(word.isBlank());
        assertTrue(words.contains(word));
    }
}