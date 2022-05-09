package org.paradise.simulation.tendermint.validator.message;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.validator.Tendermint;

@EqualsAndHashCode(callSuper = true)
public class PrevoteMessage extends TendermintMessage<String> {

    // Constructors.

    public PrevoteMessage(@NonNull SimpleAgent.AgentIdentifier sender, long height, long round, String value) {
        super(sender, height, round, value);
    }

    // Methods.

    public Tendermint.Prevote getPrevote() {
        return new Tendermint.Prevote(getHeight(), getRound(), getContent());
    }
}
