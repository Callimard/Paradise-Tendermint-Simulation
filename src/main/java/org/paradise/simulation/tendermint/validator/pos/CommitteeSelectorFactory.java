package org.paradise.simulation.tendermint.validator.pos;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommitteeSelectorFactory {

    // Methods.

    private CommitteeSelector basicCommitteeSelector() {
        return SimpleCommitteeSelector.instance();
    }

}
