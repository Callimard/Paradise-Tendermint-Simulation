package org.paradise.simulation.tendermint.validator.message;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.validator.TendermintValidator;

@EqualsAndHashCode(callSuper = true)
public class PrevoteMessage extends TendermintMessage<String> {

    // Constructors.

    public PrevoteMessage(@NonNull SimpleAgent.AgentIdentifier sender, long height, long round, String value) {
        super(sender, height, round, value);
    }

    // Methods.

    public TendermintValidator.Prevote getPrevote() {
        return new TendermintValidator.Prevote(getHeight(), getRound(), getContent());
    }
}
