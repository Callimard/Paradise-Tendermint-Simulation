package org.paradise.simulation.tendermint.seed.message;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.seed.NodeRegistration;

@EqualsAndHashCode(callSuper = true)
public class NodeRegistrationMessage extends TendermintSeedNodeMessage<NodeRegistration> {

    // Constructors.

    public NodeRegistrationMessage(@NonNull SimpleAgent.AgentIdentifier sender, @NonNull NodeRegistration content) {
        super(sender, content);
    }
}
