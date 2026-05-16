package com.example.fitnessbot.service;

import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class TelegramUserProfileService {

    private final UserRepository userRepository;

    public TelegramUserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void recordTelegramUser(org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        if (telegramUser == null || telegramUser.getId() == null) {
            return;
        }

        String telegramUsername = telegramUser.getUserName();
        User user = userRepository.findByTelegramId(telegramUser.getId())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramUser.getId());
                    return newUser;
                });

        String previousUsername = user.getTelegramUsername();
        user.setTelegramUsername(telegramUsername);

        if (user.getId() == null || !Objects.equals(previousUsername, user.getTelegramUsername())) {
            userRepository.save(user);
        }
    }
}
