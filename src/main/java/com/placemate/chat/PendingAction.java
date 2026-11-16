package com.placemate.chat;

import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

/** Tool calls the model proposed that are waiting for the user's decision. */
record PendingAction(String id, List<AssistantMessage.ToolCall> calls) {}
