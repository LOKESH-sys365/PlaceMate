package com.placemate.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
class ConversationStore {

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Duration ttl;

    ConversationStore(@Value("${placemate.chat.conversation-ttl-minutes:30}") long ttlMinutes) {
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    Conversation getOrCreate(String id) {
        evictExpired();
        String key = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        Conversation c = conversations.computeIfAbsent(key, Conversation::new);
        c.touch();
        return c;
    }

    Conversation find(String id) {
        evictExpired();
        if (id == null) {
            return null;
        }
        Conversation c = conversations.get(id);
        if (c != null) {
            c.touch();
        }
        return c;
    }

    private void evictExpired() {
        Instant cutoff = Instant.now().minus(ttl);
        conversations.values().removeIf(c -> c.lastAccess().isBefore(cutoff));
    }
}
