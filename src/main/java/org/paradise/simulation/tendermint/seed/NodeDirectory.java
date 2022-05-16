package org.paradise.simulation.tendermint.seed;

import org.paradise.palmbeach.core.agent.SimpleAgent;

import java.util.Set;

public record NodeDirectory(Set<SimpleAgent.AgentIdentifier> seedNodes, Set<SimpleAgent.AgentIdentifier> validators,
                            Set<SimpleAgent.AgentIdentifier> fullNodes) {
}
