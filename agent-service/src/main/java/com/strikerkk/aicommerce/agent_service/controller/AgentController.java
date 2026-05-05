package com.strikerkk.aicommerce.agent_service.controller;

import com.strikerkk.aicommerce.agent_service.common.ApiResponse;
import com.strikerkk.aicommerce.agent_service.dto.request.ChatRequest;
import com.strikerkk.aicommerce.agent_service.dto.request.StartSessionRequest;
import com.strikerkk.aicommerce.agent_service.dto.response.*;
import com.strikerkk.aicommerce.agent_service.service.AgentSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentSessionService agentSessionService;

    @PostMapping("/chat")
    ResponseEntity<ApiResponse<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Chat processed"));
    }

    @PostMapping("/session/start")
    ResponseEntity<ApiResponse<StartSessionResponse>> startSession(@RequestBody(required = false) StartSessionRequest request) {

        StartSessionResponse response = agentSessionService.startSession(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Session started successfully", response));
    }

    @GetMapping("/session/{sessionId}")
    ResponseEntity<ApiResponse<SessionStatusResponse>> getSessionStatus(@PathVariable UUID sessionId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session fetched successfully"));
    }

    @DeleteMapping("/session/{sessionId}/end")
    ResponseEntity<ApiResponse<Void>> endSession(@PathVariable UUID sessionId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session ended successfully"));
    }

    @GetMapping("/session/{sessionId}/history")
    ResponseEntity<ApiResponse<ConversationHistoryResponse>> getConversationHistory(@PathVariable UUID sessionId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session history fetched successfully"));
    }

    @GetMapping("/session/my")
    ResponseEntity<ApiResponse<MySessionResponse>> getMySession() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("My all sessions fetched successfully"));
    }

    @GetMapping("/session/{sessionId}/actions")
    ResponseEntity<ApiResponse<SessionActionResponse>> getSessionAction(@PathVariable UUID sessionId) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session actions fetched successfully"));
    }
}
