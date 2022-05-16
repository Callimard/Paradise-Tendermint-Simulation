package org.paradise.simulation.tendermint.client.message;

import org.paradise.palmbeach.basic.messaging.Message;

public abstract class TendermintClientMessage<T> extends Message<T> {

    // Constructors.

    protected TendermintClientMessage(T content) {
        super(content);
    }
}
