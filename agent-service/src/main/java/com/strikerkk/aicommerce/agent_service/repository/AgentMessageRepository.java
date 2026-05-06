package com.strikerkk.aicommerce.agent_service.repository;

import com.strikerkk.aicommerce.agent_service.entity.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {
    List<AgentMessage> findBySession_SessionIdOrderBySequenceNumberAsc(UUID sessionId);

    int countBySession_SessionId(UUID sessionId);
}
