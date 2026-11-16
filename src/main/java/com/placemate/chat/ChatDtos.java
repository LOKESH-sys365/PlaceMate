package com.placemate.chat;

import java.util.List;

public final class ChatDtos {
    private ChatDtos() {}

    public record ChatRequest(String conversationId, String message) {}

    public record ConfirmRequest(String conversationId, String actionId, boolean approved) {}

    public record CallView(String tool, String arguments) {}

    public record PendingView(String actionId, List<CallView> calls) {}

    /** How many tools the model was sent, versus how many exist. */
    public record ToolStats(int selected, int total, int tokensSelected, int tokensAll, List<String> names) {}

    /** status is ANSWER, CONFIRMATION_REQUIRED or ERROR. */
    public record ChatResult(String conversationId, String status, String reply,
                             PendingView pending, List<String> toolsUsed, ToolStats toolStats) {}
}
