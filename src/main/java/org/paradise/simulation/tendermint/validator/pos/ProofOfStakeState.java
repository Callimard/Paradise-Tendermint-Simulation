package org.paradise.simulation.tendermint.validator.pos;

import com.google.common.collect.Maps;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ProofOfStakeState {

    // Variables.

    private final Map<String, Long> nodes;

    @Getter
    private long totalWeight;

    // Constructors.

    public ProofOfStakeState() {
        this.nodes = Maps.newHashMap();
    }

    // Methods.

    public void clear() {
        nodes.clear();
        totalWeight = 0L;
    }

    /**
     * Map the specified address to the initial weight. If the address is already use, the old weight is replaced by the specified initial weight.
     *
     * @param address       the validator address
     * @param initialWeight the initial weight
     */
    public void initiateAddressWeight(@NonNull String address, long initialWeight) {
        Long old = nodes.put(address, initialWeight);
        if (old != null) {
            totalWeight -= old;
        }
        totalWeight += initialWeight;
    }

    /**
     * Add to the specified weight to the current weight mapped to the specified address. If the address is not already contain, initiate the weight
     * to the specified weight.
     *
     * @param address          the validator address
     * @param additionalWeight the weight to add
     */
    public void addWeight(@NonNull String address, long additionalWeight) {
        if (additionalWeight > 0) {
            nodes.merge(address, additionalWeight, Long::sum);
            totalWeight += additionalWeight;
        }
    }

    /**
     * Remove the weight to the current weight mapped to the specified address. If the result weight is less or equal to zero, the address id
     * removed.
     *
     * @param address        the validator address
     * @param weightToRemove the weight to remove
     */
    public void removeWeight(@NonNull String address, long weightToRemove) {
        if (weightToRemove > 0 && nodes.containsKey(address)) {
            long currentWeight = nodes.get(address);
            long nextWeight = currentWeight - weightToRemove;
            if (nextWeight > 0) {
                nodes.put(address, nextWeight);
                totalWeight -= weightToRemove;
            } else {
                long old = nodes.remove(address);
                totalWeight -= old;
            }
        }
    }

    public Node getNode(@NonNull String address) {
        if (nodes.containsKey(address)) {
            return new Node(address, nodes.get(address));
        } else
            return null;
    }

    public Set<Node> allNodes() {
        return nodes.entrySet().stream().map(entry -> new Node(entry.getKey(), entry.getValue())).collect(Collectors.toSet());
    }

    // Inner classes.

    public static record Node(@NonNull String address, long weight) {
    }
}
