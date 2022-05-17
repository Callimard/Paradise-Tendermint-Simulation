package org.paradise.simulation.tendermint.validator.pos;

import lombok.NonNull;

import java.util.Set;
import java.util.random.RandomGenerator;

public interface CommitteeSelector {

    Set<String> selectCommittee(ProofOfStakeState posState, int committeeSize, @NonNull RandomGenerator randomGenerator);

}
