package com.placemate.chat;

import com.placemate.chat.ChatDtos.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResult> chat(@RequestBody ChatRequest request) {
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(new ChatResult(request.conversationId(), "ERROR",
                    "Type a message first.", null, java.util.List.of(), null));
        }
        return ResponseEntity.ok(chatService.chat(request.conversationId(), request.message().trim()));
    }

    @PostMapping("/chat/confirm")
    public ChatResult confirm(@RequestBody ConfirmRequest request) {
        return chatService.confirm(request.conversationId(), request.actionId(), request.approved());
    }

    @GetMapping("/tools/preview")
    public ToolStats preview(@RequestParam("q") String query) {
        return chatService.previewSelection(query);
    }
}
