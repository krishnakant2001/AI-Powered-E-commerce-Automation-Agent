package com.strikerkk.aicommerce.agent_service.repository;

import com.strikerkk.aicommerce.agent_service.entity.AgentAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {
    List<AgentAction> findBySession_SessionIdOrderByCreatedAtAsc(UUID sessionId);
}
