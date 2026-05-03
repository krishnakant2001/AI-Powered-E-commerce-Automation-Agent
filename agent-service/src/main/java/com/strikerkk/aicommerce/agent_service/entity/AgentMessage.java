package com.strikerkk.aicommerce.agent_service.entity;

import com.strikerkk.aicommerce.agent_service.entity.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agent_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "message_id", updatable = false, nullable = false)
    private UUID messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AgentSession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private MessageRole role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_name")
    private String toolName;

    // JSON payload sent to the tool (your microservice request body)
    @Column(name = "toot_input", columnDefinition = "TEXT")
    private String toolInput;

    // JSON response received back from the tool (your microservice response)
    @Column(name = "tool_output", columnDefinition = "TEXT")
    private String toolOutput;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
