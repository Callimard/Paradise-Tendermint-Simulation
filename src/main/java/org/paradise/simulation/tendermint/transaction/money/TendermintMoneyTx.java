package org.paradise.simulation.tendermint.transaction.money;

import com.google.common.primitives.Longs;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.codec.digest.DigestUtils.sha256;

@EqualsAndHashCode(callSuper = true)
public class TendermintMoneyTx extends TendermintAmountTx {

    // Variables.

    @Getter
    @NonNull
    private final String receiver;

    @Getter
    private final long fees;

    // Constructors.

    public TendermintMoneyTx(long timestamp, @NonNull String sender, @NonNull String receiver, long amount, long fees) {
        super(timestamp, sender, amount);
        this.receiver = receiver;
        this.fees = fees;
    }

    // Methods.

    @Override
    public String sha256Base64Hash() {
        String senderHash = encodeBase64String(sha256(getSender()));
        String receiverHash = encodeBase64String(sha256(getReceiver()));
        String amountHash = encodeBase64String(sha256(Longs.toByteArray(getAmount())));
        String feesHash = encodeBase64String(sha256(Longs.toByteArray(getFees())));

        String concat = senderHash + receiverHash + amountHash + feesHash;

        return encodeBase64String(sha256(concat));
    }

    @Override
    public boolean isBasicValid() {
        return super.isBasicValid() && !receiver.isBlank() && fees > 0L;
    }
}
