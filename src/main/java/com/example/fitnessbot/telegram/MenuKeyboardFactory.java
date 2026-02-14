package com.example.fitnessbot.telegram;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

public interface MenuKeyboardFactory {
    InlineKeyboardMarkup createMainMenuKeyboard(Long userId);
}