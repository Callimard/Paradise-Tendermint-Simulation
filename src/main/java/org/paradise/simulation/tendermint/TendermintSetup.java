package org.paradise.simulation.tendermint;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.blockchain.block.Block;
import org.paradise.palmbeach.blockchain.block.Blockchain;
import org.paradise.palmbeach.blockchain.block.NonForkBlockchain;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.environment.network.Network;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.core.simulation.SimulationSetup;
import org.paradise.simulation.tendermint.client.TendermintClient;
import org.paradise.simulation.tendermint.seed.TendermintSeedNode;
import org.paradise.simulation.tendermint.transaction.TendermintTransaction;
import org.paradise.simulation.tendermint.transaction.money.TendermintLockStakeTx;
import org.paradise.simulation.tendermint.transaction.money.TendermintMoneyTx;
import org.paradise.simulation.tendermint.validator.TendermintValidator;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.paradise.simulation.tendermint.seed.TendermintSeedNode.ROOT_SEED_NODE_PREFIX;
import static org.paradise.simulation.tendermint.seed.TendermintSeedNode.UNKNOWN_SEED_NODE_PREFIX;

@Slf4j
public class TendermintSetup implements SimulationSetup {

    private static final Random R = new Random();

    @Override
    public void setupSimulation() {
        log.info("Start all agents");
        Network network = PalmBeachSimulation.getEnvironment("simpleEnvironment").getNetwork("fullyConnectedNetwork");
        Set<SimpleAgent.AgentIdentifier> rootSeedNodes = rootSeedNodes();
        log.info("RootSeedNodes = {}", rootSeedNodes);

        Set<TendermintValidator> validators = PalmBeachSimulation.allAgents()
                .stream().filter(this::isValidator)
                .map(agent -> agent.getProtocol(TendermintValidator.class))
                .collect(Collectors.toSet());
        BlockchainInitiator blockchainInitiator = new BlockchainInitiator();
        Blockchain<TendermintTransaction> initialBC = blockchainInitiator.generateBlockchain(validators);

        for (SimpleAgent agent : PalmBeachSimulation.allAgents()) {
            setupTendermintValidatorProtocol(network, rootSeedNodes, agent, initialBC);
            setupTendermintClientProtocol(network, agent);
            setupTendermintSeedNodeProtocol(network, rootSeedNodes, agent);
        }

        TendermintFinisher.setBegin(System.currentTimeMillis());
    }

    private boolean isValidator(SimpleAgent agent) {
        return agent.getProtocol(TendermintValidator.class) != null;
    }

    private void setupTendermintValidatorProtocol(Network network, Set<SimpleAgent.AgentIdentifier> rootSeedNodes, SimpleAgent agent,
                                                  Blockchain<TendermintTransaction> initialBC) {
        TendermintValidator validator = agent.getProtocol(TendermintValidator.class);
        if (validator != null) {
            validator.setNetwork(network);
            validator.getSeedNodes().addAll(rootSeedNodes);
            validator.setDecision(initialBC);
            validator.reComputePoSState();
        }
    }

    private void setupTendermintClientProtocol(Network network, SimpleAgent agent) {
        TendermintClient tendermintClient = agent.getProtocol(TendermintClient.class);
        if (tendermintClient != null)
            tendermintClient.setNetwork(network);
    }

    private void setupTendermintSeedNodeProtocol(Network network, Set<SimpleAgent.AgentIdentifier> rootSeedNodes, SimpleAgent agent) {
        TendermintSeedNode tendermintSeedNode = agent.getProtocol(TendermintSeedNode.class);
        if (tendermintSeedNode != null) {
            tendermintSeedNode.setNetwork(network);
            tendermintSeedNode.getSeedNodes().addAll(rootSeedNodes);
        }

        if (isUnknownSeedNode(agent.getIdentifier())) {
            PalmBeachSimulation.scheduler().scheduleOnce(agent::start, R.nextInt(2000, 15000));
        } else {
            agent.start();
        }
    }

    private Set<SimpleAgent.AgentIdentifier> rootSeedNodes() {
        Set<SimpleAgent.AgentIdentifier> rootSeedNodes = Sets.newHashSet();
        for (SimpleAgent agent : PalmBeachSimulation.allAgents()) {
            SimpleAgent.AgentIdentifier identifier = agent.getIdentifier();
            if (identifier.getAgentName().contains(ROOT_SEED_NODE_PREFIX)) {
                rootSeedNodes.add(identifier);
            }
        }

        return rootSeedNodes;
    }

    private boolean isUnknownSeedNode(SimpleAgent.AgentIdentifier agent) {
        return agent.getAgentName().contains(UNKNOWN_SEED_NODE_PREFIX);
    }

    // Inner classes.

    private static final class BlockchainInitiator {

        // Variables.

        private final Map<String, Long> remainingCoin;

        // Constructors.

        public BlockchainInitiator() {
            this.remainingCoin = Maps.newHashMap();
        }

        public Blockchain<TendermintTransaction> generateBlockchain(Set<TendermintValidator> validators) {
            clear();
            final List<TendermintValidator> vList = Lists.newArrayList(validators);
            Blockchain<TendermintTransaction> bc = new NonForkBlockchain<>(new Block<>(Block.GENESIS_BLOCK_HEIGHT,
                                                                                       Block.GENESIS_BLOCK_TIMESTAMP,
                                                                                       Block.GENESIS_BLOCK_PREVIOUS,
                                                                                       Sets.newHashSet()));
            generateTotalCoin(validators);
            generateStake(validators, bc);
            spendRemainingCoin(validators, vList, bc);

            return bc;
        }

        private void generateTotalCoin(Set<TendermintValidator> validators) {
            for (TendermintValidator validator : validators) {
                long total = R.nextLong(10L, 2000L);
                remainingCoin.put(validator.getAddress(), total);
            }
        }

        private void generateStake(Set<TendermintValidator> validators, Blockchain<TendermintTransaction> bc) {
            Set<TendermintTransaction> allTx = Sets.newHashSet();
            for (TendermintValidator validator : validators) {
                if (remainingCoin.containsKey(validator.getAddress())) {
                    long remaining = remainingCoin.get(validator.getAddress());
                    long amount = R.nextLong(1L, (remaining / 2) + 2L);

                    TendermintLockStakeTx stakeTx = new TendermintLockStakeTx(1L, validator.getAddress(), amount, 50L);
                    allTx.add(stakeTx);

                    updateRemainingCoin(validator, remaining, amount);
                }
            }
            Block<TendermintTransaction> block = new Block<>(bc.currentHeight() + 1,
                                                             1L,
                                                             bc.getBlock(bc.currentHeight()).sha256Base64Hash(),
                                                             allTx);
            bc.addBlock(block);
        }

        private void updateRemainingCoin(TendermintValidator validator, long remaining, long amount) {
            long rest = remaining - amount;
            if (rest <= 0) {
                remainingCoin.remove(validator.getAddress());
            } else {
                remainingCoin.put(validator.getAddress(), rest);
            }
        }

        private void spendRemainingCoin(Set<TendermintValidator> validators, List<TendermintValidator> vList, Blockchain<TendermintTransaction> bc) {
            while (!remainingCoin.isEmpty()) {
                Set<TendermintTransaction> allTx = Sets.newHashSet();
                for (TendermintValidator validator : validators) {
                    if (remainingCoin.containsKey(validator.getAddress())) {
                        long remaining = remainingCoin.get(validator.getAddress());
                        TendermintValidator receiver = randomSelection(vList, validator);
                        long amount = R.nextLong(1L, remaining + 1L);

                        TendermintMoneyTx moneyTx = new TendermintMoneyTx(1L, validator.getAddress(), receiver.getAddress(), amount, 0L);
                        allTx.add(moneyTx);

                        updateRemainingCoin(validator, remaining, amount);
                    }
                }
                Block<TendermintTransaction> block = new Block<>(bc.currentHeight() + 1,
                                                                 1L,
                                                                 bc.getBlock(bc.currentHeight()).sha256Base64Hash(),
                                                                 allTx);
                bc.addBlock(block);
            }
        }

        private TendermintValidator randomSelection(List<TendermintValidator> l, TendermintValidator except) {
            TendermintValidator selected = null;
            do {
                int index = R.nextInt(0, l.size());
                selected = l.get(index);
            } while (!selected.equals(except));
            return selected;
        }

        private void clear() {
            remainingCoin.clear();
        }
    }
}
