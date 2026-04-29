package com.example.fitnessbot.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgramRenameSessionManager {

    private final Map<Long, Long> pendingRenames = new ConcurrentHashMap<>();

    public void startRename(Long telegramUserId, Long programId) {
        pendingRenames.put(telegramUserId, programId);
    }

    public boolean hasPendingRename(Long telegramUserId) {
        return pendingRenames.containsKey(telegramUserId);
    }

    public Long getProgramId(Long telegramUserId) {
        return pendingRenames.get(telegramUserId);
    }

    public void endRename(Long telegramUserId) {
        pendingRenames.remove(telegramUserId);
    }
}
