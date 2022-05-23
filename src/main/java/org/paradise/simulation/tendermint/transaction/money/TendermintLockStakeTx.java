package org.paradise.simulation.tendermint.transaction.money;

import com.google.common.primitives.Longs;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.paradise.palmbeach.blockchain.transaction.Transaction;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.codec.digest.DigestUtils.sha256;

@EqualsAndHashCode(callSuper = true)
public class TendermintLockStakeTx extends TendermintAmountTx {

    // Constants.

    public static final long MINIMAL_MIN_TIME = 15;

    // Variables.

    @Getter
    private final long minTime;

    // Constructors.

    public TendermintLockStakeTx(long timestamp, @NonNull String sender, long amount, long minTime) {
        super(timestamp, sender, amount);
        this.minTime = minTime;
    }

    // Methods.

    @Override
    public Transaction copy() {
        return new TendermintLockStakeTx(getTimestamp(), getSender(), getAmount(), getMinTime());
    }

    @Override
    public String sha256Base64Hash() {
        String senderHash = encodeBase64String(sha256(getSender()));
        String amountHash = encodeBase64String(sha256(Longs.toByteArray(getAmount())));
        String minTimeHash = encodeBase64String(sha256(Longs.toByteArray(getMinTime())));

        String concat = senderHash + amountHash + minTimeHash;

        return encodeBase64String(sha256(concat));
    }

    @Override
    public boolean isBasicValid() {
        return super.isBasicValid() && minTime >= MINIMAL_MIN_TIME;
    }
}
