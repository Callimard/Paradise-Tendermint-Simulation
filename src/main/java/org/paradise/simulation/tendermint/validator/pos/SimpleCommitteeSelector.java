package org.paradise.simulation.tendermint.validator.pos;

import com.google.common.collect.Sets;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Set;
import java.util.SortedSet;
import java.util.random.RandomGenerator;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleCommitteeSelector implements CommitteeSelector {

    // Constants.

    private static final SimpleCommitteeSelector INSTANCE = new SimpleCommitteeSelector();

    // Methods.

    public static SimpleCommitteeSelector instance() {
        return INSTANCE;
    }

    @Override
    public Set<String> selectCommittee(ProofOfStakeState posState, int committeeSize, @NonNull RandomGenerator randomGenerator) {
        final Set<String> committee = Sets.newHashSet();

        final Set<ProofOfStakeState.Node> nodes = posState.allNodes();
        final long totalWeight = posState.getTotalWeight();

        final Interval[] intervalArray = new Interval[nodes.size()];
        long ite = 1L;
        int i = 0;
        for (ProofOfStakeState.Node node : nodes) {
            intervalArray[i] = new Interval(node, ite, ite + (node.weight() - 1));
            ite += node.weight();
            i++;
        }

        Set<Interval> removedInterval = Sets.newHashSet();
        SortedSet<IndexWeight> accumulatorSet = Sets.newTreeSet();

        for (int c = 0; c < committeeSize; c++) {
            long totalAccumulation = accumulator(accumulatorSet, intervalArray.length);
            long rGenerated = randomGenerator.nextLong(1, (totalWeight - totalAccumulation) + 1);
            Interval prev = null;
            for (int j = 0; j < intervalArray.length; j++) {
                Interval interval = intervalArray[j];
                boolean alreadyRemoved = removedInterval.contains(interval);

                long accumulator = accumulator(accumulatorSet, j);
                long min = interval.min - accumulator;

                if (prev != null && !alreadyRemoved && min > rGenerated) {
                    removedInterval.add(prev);
                    accumulatorSet.add(new IndexWeight(j, prev.associatedNode().weight()));
                    committee.add(prev.associatedNode().address());
                    break;
                }

                if (!alreadyRemoved) {
                    prev = interval;
                }
            }
        }

        return committee;
    }

    private static long accumulator(SortedSet<IndexWeight> accumulatorSet, int currentIndex) {
        long accumulator = 0L;
        for (IndexWeight pairIndexWeight : accumulatorSet) {
            if (pairIndexWeight.index() < currentIndex) {
                accumulator += pairIndexWeight.weight();
            } else
                break;
        }

        return accumulator;
    }

    // Inner classes.

    private static record Interval(ProofOfStakeState.Node associatedNode, long min, long max) {
    }

    private static record IndexWeight(long index, long weight) implements Comparable<IndexWeight> {
        @Override
        public int compareTo(IndexWeight o) {
            return Long.compare(index, o.index);
        }
    }
}
