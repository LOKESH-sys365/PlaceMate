package com.placemate.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.placemate.chat.ChatDtos.*;
import com.placemate.rag.ToolSelector;
import com.placemate.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Runs the tool-calling loop by hand instead of letting Spring AI execute tools internally.
 * That is what makes human approval possible: when the model asks for a tool that changes
 * data, the loop stops, remembers the proposed calls, and waits for confirm().
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatModel chatModel;
    private final ToolSelector selector;
    private final ToolRegistry registry;
    private final ConversationStore store;
    private final ObjectMapper mapper;
    private final int maxSteps;

    ChatService(ChatModel chatModel,
                ToolSelector selector,
                ToolRegistry registry,
                ConversationStore store,
                ObjectMapper mapper,
                @Value("${placemate.chat.max-steps:5}") int maxSteps) {
        this.chatModel = chatModel;
        this.selector = selector;
        this.registry = registry;
        this.store = store;
        this.mapper = mapper;
        this.maxSteps = maxSteps;
    }

    public ChatResult chat(String conversationId, String userMessage) {
        Conversation conv = store.getOrCreate(conversationId);
        synchronized (conv) {
            if (conv.pending != null) {
                return error(conv, "A change is waiting for your decision. Apply or discard it first.");
            }
            if (conv.history.isEmpty()) {
                conv.history.add(new SystemMessage(systemPrompt()));
            }
            conv.history.add(new UserMessage(userMessage));
            conv.activeTools = selector.select(userMessage);
            conv.stats = statsFor(conv.activeTools);
            conv.toolsUsed = new ArrayList<>();
            return runLoop(conv);
        }
    }

    public ChatResult confirm(String conversationId, String actionId, boolean approved) {
        Conversation conv = store.find(conversationId);
        if (conv == null) {
            return new ChatResult(null, "ERROR",
                    "This conversation expired. Send your request again.", null, List.of(), null);
        }
        synchronized (conv) {
            PendingAction pending = conv.pending;
            if (pending == null || !pending.id().equals(actionId)) {
                return error(conv, "There is no matching change to " + (approved ? "apply." : "discard."));
            }
            conv.pending = null;
            conv.history.add(execute(conv, pending.calls(), approved));
            return runLoop(conv);
        }
    }

    /** Which tools would be sent for this message? Used by the UI and for measuring the token savings. */
    public ToolStats previewSelection(String message) {
        return statsFor(selector.select(message));
    }

    // ------------------------------------------------------------------ loop

    private ChatResult runLoop(Conversation conv) {
        for (int step = 0; step < maxSteps; step++) {
            Prompt prompt = new Prompt(new ArrayList<>(conv.history),
                    ToolCallingChatOptions.builder()
                            .toolCallbacks(conv.activeTools)
                            .internalToolExecutionEnabled(false)
                            .build());

            AssistantMessage reply;
            try {
                reply = chatModel.call(prompt).getResult().getOutput();
            } catch (RuntimeException e) {
                log.error("Model call failed", e);
                return error(conv, "The model did not respond. Check that Ollama is running and the model is pulled.");
            }
            conv.history.add(reply);

            if (!reply.hasToolCalls()) {
                return new ChatResult(conv.id, "ANSWER", reply.getText() == null ? "" : reply.getText(),
                        null, List.copyOf(conv.toolsUsed), conv.stats);
            }

            List<AssistantMessage.ToolCall> calls = reply.getToolCalls();
            boolean needsApproval = calls.stream().anyMatch(c -> registry.requiresConfirmation(c.name()));
            if (needsApproval) {
                conv.pending = new PendingAction(UUID.randomUUID().toString(), calls);
                List<CallView> views = calls.stream().map(c -> new CallView(c.name(), c.arguments())).toList();
                return new ChatResult(conv.id, "CONFIRMATION_REQUIRED", "",
                        new PendingView(conv.pending.id(), views), List.copyOf(conv.toolsUsed), conv.stats);
            }
            conv.history.add(execute(conv, calls, true));
        }
        return error(conv, "Stopped after too many tool steps. Try a more specific request.");
    }

    private ToolResponseMessage execute(Conversation conv, List<AssistantMessage.ToolCall> calls, boolean approved) {
        List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
        for (AssistantMessage.ToolCall call : calls) {
            String data;
            if (registry.requiresConfirmation(call.name()) && !approved) {
                data = errorJson("The user declined this change. Nothing was saved.");
            } else {
                ToolCallback cb = conv.activeTools.stream()
                        .filter(t -> t.getToolDefinition().name().equals(call.name()))
                        .findFirst().orElse(null);
                if (cb == null) {
                    data = errorJson("Unknown tool: " + call.name());
                } else {
                    try {
                        data = cb.call(call.arguments());
                        conv.toolsUsed.add(call.name());
                    } catch (Exception e) {
                        log.warn("Tool {} failed: {}", call.name(), e.toString());
                        data = errorJson(rootMessage(e));
                    }
                }
            }
            responses.add(new ToolResponseMessage.ToolResponse(call.id(), call.name(), data));
        }
        return new ToolResponseMessage(responses);
    }

    // ------------------------------------------------------------------ helpers

    private ToolStats statsFor(List<ToolCallback> selected) {
        return new ToolStats(selected.size(), registry.all().size(),
                registry.approxTokens(selected), registry.approxTokens(registry.all()),
                selected.stream().map(t -> t.getToolDefinition().name()).toList());
    }

    private ChatResult error(Conversation conv, String message) {
        return new ChatResult(conv.id, "ERROR", message, null, List.copyOf(conv.toolsUsed), conv.stats);
    }

    private String errorJson(String message) {
        try {
            return mapper.writeValueAsString(Map.of("error", message));
        } catch (Exception e) {
            return "{\"error\":\"tool failed\"}";
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? cur.getClass().getSimpleName() : cur.getMessage();
    }

    private static String systemPrompt() {
        return """
                You are PlaceMate, an assistant for a computer science student preparing for placements.
                Today's date is %s.
                Use the provided tools to read or change the student's applications, DSA practice log and reminders.
                Never invent records. If you need an application id, call listApplications first.
                Changes are shown to the student for approval before they are saved, so propose them directly.
                Keep replies short and concrete.
                """.formatted(LocalDate.now());
    }
}
