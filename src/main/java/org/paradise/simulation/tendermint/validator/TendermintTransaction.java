package org.paradise.simulation.tendermint.validator;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.blockchain.transaction.MoneyTx;

@EqualsAndHashCode(callSuper = true)
public class TendermintTransaction extends MoneyTx {

    // Constructors.

    public TendermintTransaction(long timestamp, @NonNull String sender, @NonNull String receiver, long amount) {
        super(timestamp, sender, receiver, amount);
    }

    // Methods.

    @Override
    public String toString() {
        return "[" + this.getClass().getSimpleName() + ", " + sha256Base64Hash() + ", " + " sender " + getSender() + ", receiver " + getReceiver() +
                ", amount " + getAmount() + "]";
    }
}
