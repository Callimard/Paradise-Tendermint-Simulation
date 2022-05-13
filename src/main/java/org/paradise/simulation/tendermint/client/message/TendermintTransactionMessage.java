package org.paradise.simulation.tendermint.client.message;

import org.paradise.simulation.tendermint.validator.TendermintTransaction;

public class TendermintTransactionMessage extends ClientTendermintMessage<TendermintTransaction> {

    // Constructors.

    public TendermintTransactionMessage(TendermintTransaction value) {
        super(value);
    }
}
