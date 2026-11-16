package com.placemate.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.placemate.chat.ChatDtos.ChatResult;
import com.placemate.domain.DsaProblemRepository;
import com.placemate.domain.JobApplication;
import com.placemate.domain.JobApplicationRepository;
import com.placemate.domain.ReminderRepository;
import com.placemate.rag.ToolSelector;
import com.placemate.tools.PlacementTools;
import com.placemate.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final JobApplicationRepository applications = mock(JobApplicationRepository.class);
    private ChatService service;

    @BeforeEach
    void setUp() {
        ToolRegistry registry = new ToolRegistry(new PlacementTools(
                applications, mock(DsaProblemRepository.class), mock(ReminderRepository.class)));
        ToolSelector selector = mock(ToolSelector.class);
        when(selector.select(any())).thenReturn(registry.all());
        when(applications.save(any(JobApplication.class))).thenAnswer(inv -> {
            JobApplication a = inv.getArgument(0);
            a.setId(1L);
            return a;
        });
        service = new ChatService(chatModel, selector, registry, new ConversationStore(30),
                new ObjectMapper(), 5);
    }

    private static ChatResponse reply(AssistantMessage message) {
        return new ChatResponse(List.of(new Generation(message)));
    }

    private static AssistantMessage addApplicationCall() {
        return new AssistantMessage("", Map.of(), List.of(new AssistantMessage.ToolCall(
                "call-1", "function", "addApplication",
                "{\"company\":\"Razorpay\",\"role\":\"SDE Intern\"}")));
    }

    @Test
    void aChangeIsNotSavedUntilTheUserApproves() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(addApplicationCall()));

        ChatResult result = service.chat(null, "add Razorpay SDE intern");

        assertThat(result.status()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(result.pending().calls()).hasSize(1);
        assertThat(result.pending().calls().get(0).tool()).isEqualTo("addApplication");
        verify(applications, never()).save(any());
    }

    @Test
    void approvingRunsTheToolAndLetsTheModelAnswer() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(reply(addApplicationCall()))
                .thenReturn(reply(new AssistantMessage("Added Razorpay.")));

        ChatResult proposed = service.chat(null, "add Razorpay SDE intern");
        ChatResult done = service.confirm(proposed.conversationId(), proposed.pending().actionId(), true);

        verify(applications).save(any(JobApplication.class));
        assertThat(done.status()).isEqualTo("ANSWER");
        assertThat(done.toolsUsed()).contains("addApplication");
    }

    @Test
    void discardingNeverTouchesTheDatabase() {
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(reply(addApplicationCall()))
                .thenReturn(reply(new AssistantMessage("Okay, I won't add it.")));

        ChatResult proposed = service.chat(null, "add Razorpay SDE intern");
        ChatResult done = service.confirm(proposed.conversationId(), proposed.pending().actionId(), false);

        verify(applications, never()).save(any());
        assertThat(done.status()).isEqualTo("ANSWER");
    }

    @Test
    void cannotStartANewRequestWhileAChangeIsPending() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(addApplicationCall()));

        ChatResult proposed = service.chat(null, "add Razorpay SDE intern");
        ChatResult second = service.chat(proposed.conversationId(), "and what else?");

        assertThat(second.status()).isEqualTo("ERROR");
    }

    @Test
    void anActionIdFromAnotherChangeIsRejected() {
        when(chatModel.call(any(Prompt.class))).thenReturn(reply(addApplicationCall()));

        ChatResult proposed = service.chat(null, "add Razorpay SDE intern");
        ChatResult result = service.confirm(proposed.conversationId(), "not-the-real-id", true);

        assertThat(result.status()).isEqualTo("ERROR");
        verify(applications, never()).save(any());
    }

    @Test
    void aModelFailureBecomesAReadableError() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("connection refused"));

        ChatResult result = service.chat(null, "hi");

        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.reply()).contains("Ollama");
    }
}
