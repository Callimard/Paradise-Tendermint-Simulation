package org.paradise.simulation.tendermint.seed.message;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.paradise.palmbeach.basic.messaging.Message;
import org.paradise.palmbeach.core.agent.SimpleAgent;

@EqualsAndHashCode(callSuper = true)
public abstract class TendermintSeedNodeMessage<T> extends Message<T> {

    // Variables.

    @Getter
    @NonNull
    private final SimpleAgent.AgentIdentifier sender;

    // Constructors.

    protected TendermintSeedNodeMessage(@NonNull SimpleAgent.AgentIdentifier sender, T content) {
        super(content);
        this.sender = sender;
    }
}
