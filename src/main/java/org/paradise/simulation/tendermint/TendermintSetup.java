package org.paradise.simulation.tendermint;

import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.core.simulation.SimulationSetup;

@Slf4j
public class TendermintSetup implements SimulationSetup {

    @Override
    public void setupSimulation() {
        log.info("Start all agents");
        for (SimpleAgent agent : PalmBeachSimulation.allAgents()) {
            Tendermint tendermint = agent.getProtocol(Tendermint.class);
            tendermint.setNetwork(PalmBeachSimulation.getEnvironment("simpleEnvironment").getNetwork("myNetwork"));

            agent.start();
        }
    }
}
