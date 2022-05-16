package org.paradise.simulation.tendermint.transaction;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.blockchain.transaction.Transaction;

@EqualsAndHashCode(callSuper = true)
public abstract class TendermintTransaction extends Transaction {

    // Constructors.

    protected TendermintTransaction(long timestamp, @NonNull String sender) {
        super(timestamp, sender);
    }

    // Methods.

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + ", " + sha256Base64Hash() + ", " + " sender " + getSender() + ", receiver " + "]";
    }

    public boolean isBasicValid() {
        return getTimestamp() > 0L && !getSender().isBlank();
    }
}
