package com.example.fitnessbot.service;

import com.example.fitnessbot.model.User;
import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserLanguageService {

    private final UserRepository userRepository;

    public UserLanguageService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserLanguage getLanguage(Long telegramUserId) {
        if (telegramUserId == null) {
            return UserLanguage.ENGLISH;
        }

        return userRepository.findByTelegramId(telegramUserId)
                .map(User::getLanguage)
                .orElse(UserLanguage.ENGLISH);
    }

    @Transactional
    public UserLanguage setLanguage(Long telegramUserId, UserLanguage language) {
        if (telegramUserId == null) {
            return UserLanguage.ENGLISH;
        }

        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setTelegramId(telegramUserId);
                    return newUser;
                });
        user.setLanguage(language);
        return userRepository.save(user).getLanguage();
    }
}
