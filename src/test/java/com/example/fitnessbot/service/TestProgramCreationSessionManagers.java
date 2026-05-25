package com.example.fitnessbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

public final class TestProgramCreationSessionManagers {

    private TestProgramCreationSessionManagers() {
    }

    public static ProgramCreationSessionManager redisBacked() {
        return new ProgramCreationSessionManager(mockRedisTemplate(), new ObjectMapper());
    }

    private static StringRedisTemplate mockRedisTemplate() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        Map<String, String> redis = new HashMap<>();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().doAnswer(invocation -> {
            redis.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString());
        lenient().when(valueOperations.get(anyString())).thenAnswer(invocation -> redis.get(invocation.getArgument(0)));
        lenient().when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> redis.containsKey(invocation.getArgument(0)));
        lenient().when(redisTemplate.delete(anyString())).thenAnswer(invocation -> redis.remove(invocation.getArgument(0)) != null);
        lenient().when(redisTemplate.delete(org.mockito.ArgumentMatchers.<Collection<String>>any())).thenAnswer(invocation -> {
            Collection<?> keys = invocation.getArgument(0);
            long removed = 0;
            for (Object key : keys) {
                if (redis.remove(key) != null) {
                    removed++;
                }
            }
            return removed;
        });
        return redisTemplate;
    }
}
