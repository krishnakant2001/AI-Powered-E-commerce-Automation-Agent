package com.strikerkk.aicommerce.agent_service.model;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationMessage implements Serializable {
    private String role;
    private String content;
    private String toolName;
}
