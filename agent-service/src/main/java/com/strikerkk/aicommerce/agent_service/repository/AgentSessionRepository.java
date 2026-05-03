package com.strikerkk.aicommerce.agent_service.repository;

import com.strikerkk.aicommerce.agent_service.entity.AgentSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface AgentSessionRepository extends JpaRepository<AgentSession, UUID> {
}
