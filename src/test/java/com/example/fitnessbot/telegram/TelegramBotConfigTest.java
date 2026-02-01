package com.example.fitnessbot.telegram;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TelegramBotConfigTest {

    @Test
    void testTelegramBotConfigExists() {
        // Simple test to verify the class exists and can be instantiated
        TelegramBotConfig config = new TelegramBotConfig();
        assertEquals(TelegramBotConfig.class, config.getClass());
    }
}