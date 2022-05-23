package org.paradise.simulation.tendermint.validator.pos;

import com.google.common.collect.Lists;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

@Nested
@DisplayName("SimpleCommitteeSelector tests")
@Tag("SimpleCommitteeSelector")
public class SimpleCommitteeSelectorTest {

    // Constants.

    private static final int NB_NODE = 75;
    private static final int COMMITTEE_SIZE = 15;

    // Tests.

    @Nested
    @DisplayName("SimpleCommitteeSelector selectCommittee()")
    @Tag("selectCommittee")
    class SelectCommittee {

        @Test
        @DisplayName("selectCommittee() returns all agents if committee size is same as agents set")
        void committeeSizeEqualsToAgentSet() {
            ProofOfStakeState posState = generatePoSState();
            SimpleCommitteeSelector cSelector = SimpleCommitteeSelector.instance();
            RandomGenerator randomGenerator = new Random();

            assertThat(cSelector.selectCommittee(posState, NB_NODE, randomGenerator)).isNotNull().hasSize(NB_NODE);
        }

        @Test
        @DisplayName("selectCommittee() creates a committee with the specified size")
        void committeeCreateHasCorrectSize() {
            ProofOfStakeState posState = generatePoSState();
            SimpleCommitteeSelector cSelector = SimpleCommitteeSelector.instance();
            RandomGenerator randomGenerator = new Random();

            assertThat(cSelector.selectCommittee(posState, COMMITTEE_SIZE, randomGenerator)).isNotNull().hasSize(COMMITTEE_SIZE);
        }

        @Test
        @DisplayName("selectCommittee() returns same committee with RandomGenerator which has the same seed")
        void committeeWithSameSeed() {
            ProofOfStakeState posState = generatePoSState();
            SimpleCommitteeSelector cSelector = SimpleCommitteeSelector.instance();

            Random r = new Random();
            long seed = r.nextLong();
            List<Set<String>> allGeneratedCommittees = Lists.newArrayList();
            for (int i = 0; i < 5; i++) {
                RandomGenerator rGenerator = new Random(seed);
                allGeneratedCommittees.add(cSelector.selectCommittee(posState, COMMITTEE_SIZE, rGenerator));
            }

            Set<String> first = allGeneratedCommittees.get(0);
            for (int i = 1; i < allGeneratedCommittees.size(); i++) {
                assertThat(allGeneratedCommittees.get(i)).containsAll(first);
            }
        }
    }

    private ProofOfStakeState generatePoSState() {
        ProofOfStakeState posState = new ProofOfStakeState();
        Random random = new Random();
        for (int i = 0; i < NB_NODE; i++) {
            byte[] array = new byte[25];
            random.nextBytes(array);
            String address = new String(array);
            long weight = random.nextLong(1L, 3000L);
            posState.addWeight(address, weight);
        }

        return posState;
    }
}
