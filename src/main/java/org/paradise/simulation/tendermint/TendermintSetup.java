package org.paradise.simulation.tendermint;

import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.environment.network.Network;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.core.simulation.SimulationSetup;
import org.paradise.simulation.tendermint.client.TendermintClient;
import org.paradise.simulation.tendermint.validator.Tendermint;

@Slf4j
public class TendermintSetup implements SimulationSetup {

    @Override
    public void setupSimulation() {
        log.info("Start all agents");
        Network network = PalmBeachSimulation.getEnvironment("simpleEnvironment").getNetwork("fullyConnectedNetwork");
        for (SimpleAgent agent : PalmBeachSimulation.allAgents()) {
            Tendermint tendermint = agent.getProtocol(Tendermint.class);
            if (tendermint != null)
                tendermint.setNetwork(network);

            TendermintClient tendermintClient = agent.getProtocol(TendermintClient.class);
            if (tendermintClient != null)
                tendermintClient.setNetwork(network);

            agent.start();
        }

        TendermintFinisher.setBegin(System.currentTimeMillis());
    }
}
