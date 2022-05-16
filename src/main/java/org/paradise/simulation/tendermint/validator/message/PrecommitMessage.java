package org.paradise.simulation.tendermint.validator.message;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.validator.TendermintValidator;

@EqualsAndHashCode(callSuper = true)
public class PrecommitMessage extends TendermintValidatorMessage<String> {

    // Constructors.

    public PrecommitMessage(@NonNull SimpleAgent.AgentIdentifier sender, long height, long round, String value) {
        super(sender, height, round, value);
    }

    // Methods.

    public TendermintValidator.Precommit getPrecommit() {
        return new TendermintValidator.Precommit(getHeight(), getRound(), getContent());
    }
}
