package org.paradise.simulation.tendermint.seed.message;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.seed.NodeDirectory;

@EqualsAndHashCode(callSuper = true)
public class NodeDirectoryMessage extends TendermintSeedNodeMessage<NodeDirectory> {

    // Constructors.

    public NodeDirectoryMessage(@NonNull SimpleAgent.AgentIdentifier sender, @NonNull NodeDirectory content) {
        super(sender, content);
    }
}
