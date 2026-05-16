package com.example.fitnessbot.admin;

import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserRepository userRepository;

    public AdminUserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PutMapping("/{telegramUserId}/parser")
    @Transactional
    public ParserFlagResponse updateParserFlag(@PathVariable Long telegramUserId,
                                               @RequestBody ParserFlagRequest request) {
        User user = userRepository.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found for telegramId=" + telegramUserId));

        return updateParserFlag(user, request);
    }

    @PutMapping("/by-login/{telegramLogin}/parser")
    @Transactional
    public ParserFlagResponse updateParserFlagByTelegramLogin(@PathVariable String telegramLogin,
                                                              @RequestBody ParserFlagRequest request) {
        String normalizedLogin = normalizeTelegramLogin(telegramLogin);
        List<User> matches = userRepository.findByTelegramUsernameIgnoreCase(normalizedLogin);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "User not found for telegramLogin=@" + normalizedLogin);
        }
        if (matches.size() > 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Multiple users found for telegramLogin=@" + normalizedLogin);
        }

        return updateParserFlag(matches.getFirst(), request);
    }

    private ParserFlagResponse updateParserFlag(User user, ParserFlagRequest request) {
        user.setUseAiParser(request.useAiParser());
        User savedUser = userRepository.save(user);
        return new ParserFlagResponse(savedUser.getTelegramId(), savedUser.isUseAiParser());
    }

    private String normalizeTelegramLogin(String telegramLogin) {
        if (telegramLogin == null || telegramLogin.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Telegram login is required");
        }

        String normalizedLogin = telegramLogin.trim();
        if (normalizedLogin.startsWith("@")) {
            normalizedLogin = normalizedLogin.substring(1);
        }
        if (!normalizedLogin.matches("[A-Za-z0-9_]{5,32}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Telegram login: " + telegramLogin);
        }

        return normalizedLogin.toLowerCase(Locale.ROOT);
    }

    public record ParserFlagRequest(boolean useAiParser) {
    }

    public record ParserFlagResponse(Long telegramUserId, boolean useAiParser) {
    }
}
