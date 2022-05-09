package org.paradise.simulation.tendermint.client.message;

import org.paradise.palmbeach.basic.messaging.Message;

public abstract class ClientTendermintMessage<T> extends Message<T> {

    // Constructors.

    protected ClientTendermintMessage(T content) {
        super(content);
    }
}
