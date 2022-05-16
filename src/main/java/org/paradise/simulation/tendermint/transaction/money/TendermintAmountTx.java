package org.paradise.simulation.tendermint.transaction.money;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.paradise.simulation.tendermint.transaction.TendermintTransaction;

@EqualsAndHashCode(callSuper = true)
public abstract class TendermintAmountTx extends TendermintTransaction {

    // Variables.

    @Getter
    private final long amount;

    // Constructors.

    protected TendermintAmountTx(long timestamp, @NonNull String sender, long amount) {
        super(timestamp, sender);
        this.amount = amount;
    }

    // Methods.


    @Override
    public boolean isBasicValid() {
        return super.isBasicValid() && amount > 0L;
    }
}
