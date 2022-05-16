package org.paradise.simulation.tendermint.client.message;

import org.paradise.simulation.tendermint.transaction.TendermintTransaction;

public class TransactionMessage extends TendermintClientMessage<TendermintTransaction> {

    // Constructors.

    public TransactionMessage(TendermintTransaction value) {
        super(value);
    }
}
