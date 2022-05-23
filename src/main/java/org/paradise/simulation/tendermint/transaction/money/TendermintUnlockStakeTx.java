package org.paradise.simulation.tendermint.transaction.money;

import com.google.common.primitives.Longs;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import org.paradise.palmbeach.blockchain.transaction.Transaction;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.codec.digest.DigestUtils.sha256;

@EqualsAndHashCode(callSuper = true)
public class TendermintUnlockStakeTx extends TendermintAmountTx {

    // Constructors.

    public TendermintUnlockStakeTx(long timestamp, @NonNull String sender, long amount) {
        super(timestamp, sender, amount);
    }

    // Methods.

    @Override
    public Transaction copy() {
        return new TendermintUnlockStakeTx(getTimestamp(), getSender(), getAmount());
    }

    @Override
    public String sha256Base64Hash() {
        String senderHash = encodeBase64String(sha256(getSender()));
        String amountHash = encodeBase64String(sha256(Longs.toByteArray(getAmount())));

        String concat = senderHash + amountHash;

        return encodeBase64String(sha256(concat));
    }
}
