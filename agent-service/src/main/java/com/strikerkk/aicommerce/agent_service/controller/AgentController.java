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

        SessionStatusResponse response = agentSessionService.getSessionStatus(sessionId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session fetched successfully", response));
    }

    @DeleteMapping("/session/{sessionId}/end")
    ResponseEntity<ApiResponse<Void>> endSession(@PathVariable UUID sessionId) {

        agentSessionService.endSession(sessionId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session ended successfully"));
    }

    @GetMapping("/session/{sessionId}/history")
    ResponseEntity<ApiResponse<ConversationHistoryResponse>> getConversationHistory(@PathVariable UUID sessionId) {

        ConversationHistoryResponse response = agentSessionService.getConversationHistory(sessionId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session history fetched successfully", response));
    }

    @GetMapping("/session/my")
    ResponseEntity<ApiResponse<MySessionResponse>> getMySession() {

        MySessionResponse response = agentSessionService.getMySessions();

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("My all sessions fetched successfully", response));
    }

    @GetMapping("/session/{sessionId}/actions")
    ResponseEntity<ApiResponse<SessionActionResponse>> getSessionAction(@PathVariable UUID sessionId) {

        SessionActionResponse response = agentSessionService.getSessionActions(sessionId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResponse.success("Session actions fetched successfully", response));
    }
}
