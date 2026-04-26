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

        user.setUseAiParser(request.enabled());
        User savedUser = userRepository.save(user);
        return new ParserFlagResponse(savedUser.getTelegramId(), savedUser.isUseAiParser());
    }

    public record ParserFlagRequest(boolean enabled) {
    }

    public record ParserFlagResponse(Long telegramUserId, boolean useAiParser) {
    }
}
