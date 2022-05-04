package org.paradise.simulation.tendermint;

import org.paradise.palmbeach.core.simulation.PalmBeachRunner;
import org.paradise.palmbeach.core.simulation.exception.RunSimulationErrorException;

public class TendermintMain {

    public static void main(String[] args) throws RunSimulationErrorException {
        PalmBeachRunner.launchSimulation(TendermintMain.class, args);
    }
}
