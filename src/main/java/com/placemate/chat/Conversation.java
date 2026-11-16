package com.placemate.chat;

import com.placemate.chat.ChatDtos.ToolStats;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** State for one chat. Held in memory; see README for the trade-off. */
class Conversation {
    final String id;
    final List<Message> history = new ArrayList<>();
    List<ToolCallback> activeTools = List.of();
    List<String> toolsUsed = new ArrayList<>();
    ToolStats stats;
    PendingAction pending;
    private volatile Instant lastAccess = Instant.now();

    Conversation(String id) {
        this.id = id;
    }

    void touch() {
        lastAccess = Instant.now();
    }

    Instant lastAccess() {
        return lastAccess;
    }
}
