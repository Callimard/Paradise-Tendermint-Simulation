package org.paradise.simulation.tendermint.validator.message;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.paradise.palmbeach.basic.messaging.Message;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.validator.TendermintValidator;

@EqualsAndHashCode(callSuper = true)
public abstract class TendermintMessage<T> extends Message<T> {

    // Variables.

    @Getter
    @NonNull
    private final SimpleAgent.AgentIdentifier sender;

    @Getter
    private final long height;

    @Getter
    private final long round;

    // Constructors.

    protected TendermintMessage(@NonNull SimpleAgent.AgentIdentifier sender, long height, long round, T value) {
        super(value);
        this.sender = sender;
        this.height = height;
        this.round = round;
    }

    // Getters.

    public TendermintValidator.Stage getStage() {
        return new TendermintValidator.Stage(height, round);
    }

    public T getValue() {
        return getContent();
    }
}
