package org.paradise.simulation.tendermint.validator.pos;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommitteeSelectorFactory {

    // Methods.

    public static CommitteeSelector basicCommitteeSelector() {
        return SimpleCommitteeSelector.instance();
    }

}
