package org.paradise.simulation.tendermint;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.environment.network.Network;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.core.simulation.SimulationSetup;
import org.paradise.simulation.tendermint.client.TendermintClient;
import org.paradise.simulation.tendermint.seed.TendermintSeedNode;
import org.paradise.simulation.tendermint.validator.TendermintValidator;

import java.util.Random;
import java.util.Set;

import static org.paradise.simulation.tendermint.seed.TendermintSeedNode.ROOT_SEED_NODE_PREFIX;
import static org.paradise.simulation.tendermint.seed.TendermintSeedNode.UNKNOWN_SEED_NODE_PREFIX;

@Slf4j
public class TendermintSetup implements SimulationSetup {

    private final Random r = new Random();

    @Override
    public void setupSimulation() {
        log.info("Start all agents");
        Network network = PalmBeachSimulation.getEnvironment("simpleEnvironment").getNetwork("fullyConnectedNetwork");
        Set<SimpleAgent.AgentIdentifier> rootSeedNodes = rootSeedNodes();
        log.info("RootSeedNodes = {}", rootSeedNodes);
        for (SimpleAgent agent : PalmBeachSimulation.allAgents()) {
            TendermintValidator tendermintValidator = agent.getProtocol(TendermintValidator.class);
            if (tendermintValidator != null) {
                tendermintValidator.setNetwork(network);
                tendermintValidator.getSeedNodes().addAll(rootSeedNodes);
            }

            TendermintClient tendermintClient = agent.getProtocol(TendermintClient.class);
            if (tendermintClient != null)
                tendermintClient.setNetwork(network);

            TendermintSeedNode tendermintSeedNode = agent.getProtocol(TendermintSeedNode.class);
            if (tendermintSeedNode != null) {
                tendermintSeedNode.setNetwork(network);
                tendermintSeedNode.getSeedNodes().addAll(rootSeedNodes);
            }

            if (isUnknownSeedNode(agent.getIdentifier())) {
                PalmBeachSimulation.scheduler().scheduleOnce(agent::start, r.nextInt(2000, 15000));
            } else {
                agent.start();
            }
        }

        TendermintFinisher.setBegin(System.currentTimeMillis());
    }

    private Set<SimpleAgent.AgentIdentifier> rootSeedNodes() {
        Set<SimpleAgent.AgentIdentifier> rootSeedNodes = Sets.newHashSet();
        for (SimpleAgent agent : PalmBeachSimulation.allAgents()) {
            SimpleAgent.SimpleAgentIdentifier identifier = (SimpleAgent.SimpleAgentIdentifier) agent.getIdentifier();
            if (identifier.getAgentName().contains(ROOT_SEED_NODE_PREFIX)) {
                rootSeedNodes.add(identifier);
            }
        }

        return rootSeedNodes;
    }

    private boolean isUnknownSeedNode(SimpleAgent.AgentIdentifier agent) {
        SimpleAgent.SimpleAgentIdentifier identifier = (SimpleAgent.SimpleAgentIdentifier) agent;
        return identifier.getAgentName().contains(UNKNOWN_SEED_NODE_PREFIX);
    }
}
