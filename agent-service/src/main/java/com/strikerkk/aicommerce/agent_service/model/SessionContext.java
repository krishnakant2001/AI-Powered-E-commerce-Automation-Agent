package com.strikerkk.aicommerce.agent_service.model;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionContext implements Serializable {

    /**
     * Redis Model
     * Stored as JSON in Redis with key: session:{sessionId}
     * This is what gets sent to the LLM on every turn.
     * It's the agent's working memory for the current conversation.
     */

    private UUID sessionId;
    private Long userId;
    private String userEmail;

    @Builder.Default
    private List<ConversationMessage> conversationMessages = new ArrayList<>();

    private String currentIntent;
    private String pendingClarificationFor;

    private String lastProductId;
    private String lastVariantId;
    private String lastOrderId;
    private LocalDateTime lastActivityAt;

}
