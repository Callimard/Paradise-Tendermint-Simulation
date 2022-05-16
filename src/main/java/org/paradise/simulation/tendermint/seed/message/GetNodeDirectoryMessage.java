package org.paradise.simulation.tendermint.seed.message;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.core.agent.SimpleAgent;


@EqualsAndHashCode(callSuper = true)
public class GetNodeDirectoryMessage extends TendermintSeedNodeMessage<Void> {

    // Constructors.

    public GetNodeDirectoryMessage(@NonNull SimpleAgent.AgentIdentifier sender) {
        super(sender, null);
    }
}
