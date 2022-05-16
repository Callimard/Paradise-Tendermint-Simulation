package org.paradise.simulation.tendermint.seed;

import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.NodeType;

public record NodeRegistration(SimpleAgent.AgentIdentifier agent, NodeType nodeType) {
}
