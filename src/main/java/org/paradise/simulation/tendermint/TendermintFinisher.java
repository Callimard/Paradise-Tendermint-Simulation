package org.paradise.simulation.tendermint;

import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.core.simulation.SimulationFinisher;

@Slf4j
public class TendermintFinisher implements SimulationFinisher {

    private static double begin = -1.d;

    @Override

    public void finishSimulation() {
        double end = System.currentTimeMillis();
        log.info("Simulation begin {}", begin);
        log.info("Simulation end {}", end);
        double duration = (end - begin) / 1000.d;
        log.info("Simulation duration {} second", duration);
    }

    public static void setBegin(double begin) {
        TendermintFinisher.begin = begin;
    }
}
