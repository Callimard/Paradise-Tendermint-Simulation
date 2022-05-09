package org.paradise.simulation.tendermint;

import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.blockchain.block.Block;
import org.paradise.palmbeach.blockchain.block.Blockchain;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.core.simulation.SimulationFinisher;
import org.paradise.simulation.tendermint.validator.Tendermint;
import org.paradise.simulation.tendermint.validator.TendermintTransaction;

import java.util.List;

@Slf4j
public class TendermintFinisher implements SimulationFinisher {

    private static double begin = -1.d;

    private static final int DISPLAY_BLOCK_WIDTH = 115;

    @Override

    public void finishSimulation() {
        displayDuration();
        displayGSTAverage();
        displayBlockchainVerification();
        displayBlockchain();
    }

    public static void setBegin(double begin) {
        TendermintFinisher.begin = begin;
    }

    public void displayDuration() {
        displayLogTitle("Duration");
        double end = System.currentTimeMillis();
        log.info("Simulation begin {}", begin);
        log.info("Simulation end {}", end);
        double duration = (end - begin) / 1000.d;
        log.info("Simulation duration {} second", duration);
    }

    private void displayGSTAverage() {
        displayLogTitle("GST Average");

        List<SimpleAgent> agents = PalmBeachSimulation.allAgents();

        double timeoutProposeAverage = 0L;
        double timeoutPrevoteAverage = 0L;
        double timeoutPrecommitAverage = 0L;

        double nbValidator = 0.d;
        for (SimpleAgent agent : agents) {
            if (isTendermintValidator(agent)) {
                nbValidator++;
                Tendermint tendermint = agent.getProtocol(Tendermint.class);
                timeoutProposeAverage += tendermint.getTimeoutPropose();
                timeoutPrevoteAverage += tendermint.getTimeoutPrevote();
                timeoutPrecommitAverage += tendermint.getTimeoutPrecommit();
            }
        }

        if (nbValidator != 0.d) {
            timeoutProposeAverage = timeoutProposeAverage / nbValidator;
            timeoutPrevoteAverage = timeoutPrevoteAverage / nbValidator;
            timeoutPrecommitAverage = timeoutPrecommitAverage / nbValidator;

            log.info("Number of Validator = {}", nbValidator);
            log.info("TimeoutProposeAverage = {}", timeoutProposeAverage);
            log.info("TimeoutPrevoteAverage = {}", timeoutPrevoteAverage);
            log.info("TimeoutPrecommitAverage = {}", timeoutPrecommitAverage);
        } else
            log.error("Nb validators is equal to 0");
    }

    private void displayBlockchainVerification() {
        displayLogTitle("Blockchain Verification");

        boolean allHeightCorrect = true;

        List<SimpleAgent> agents = PalmBeachSimulation.allAgents();
        List<Blockchain<TendermintTransaction>> allBlockchains = getAllAgentBlockchain(agents);

        Pair<Long, Long> pairMinMaxHeight = displayMinMaxHeight(allBlockchains);

        for (int i = 0; i < pairMinMaxHeight.second(); i++) {
            List<Block<TendermintTransaction>> blockAtHeight = fillBlockAtHeight(allBlockchains, pairMinMaxHeight, i);
            if (!isCorrectAtHeight(blockAtHeight, i)) {
                allHeightCorrect = false;
            }
        }

        log.info("Blockchain is correct = {}", allHeightCorrect);
    }

    private Pair<Long, Long> displayMinMaxHeight(List<Blockchain<TendermintTransaction>> allBlockchains) {
        long minHeight = Long.MAX_VALUE;
        long maxHeight = Long.MIN_VALUE;
        for (Blockchain<TendermintTransaction> bc : allBlockchains) {
            if (minHeight > bc.currentHeight()) {
                minHeight = bc.currentHeight();
            }

            if (maxHeight < bc.currentHeight()) {
                maxHeight = bc.currentHeight();
            }
        }

        log.info("MinHeight = {}", minHeight);
        log.info("MaxHeight = {}", maxHeight);

        return new Pair<>(minHeight, maxHeight);
    }

    private boolean isCorrectAtHeight(List<Block<TendermintTransaction>> blockAtHeight, int i) {
        boolean correct = true;
        Block<TendermintTransaction> previous = blockAtHeight.get(0);
        for (int j = 1; j < blockAtHeight.size(); j++) {
            Block<TendermintTransaction> current = blockAtHeight.get(j);
            if (!current.sha256Base64Hash().equals(previous.sha256Base64Hash())) {
                log.error("Different block for the height {}, {} != {}", i, previous.sha256Base64Hash(), current.sha256Base64Hash());
                correct = false;
            }
            previous = current;
        }
        return correct;
    }

    private List<Block<TendermintTransaction>> fillBlockAtHeight(List<Blockchain<TendermintTransaction>> allBlockchains,
                                                                 Pair<Long, Long> pairMinMaxHeight, int i) {
        List<Block<TendermintTransaction>> blockAtHeight = Lists.newArrayList();
        for (Blockchain<TendermintTransaction> bc : allBlockchains) {
            Block<TendermintTransaction> block = bc.getBlock(i);
            if (block != null) {
                blockAtHeight.add(block);
            } else {
                if (i <= pairMinMaxHeight.first()) {
                    log.error("Null block for blockchain {} at height {}", bc, i);
                }
            }
        }

        return blockAtHeight;
    }

    private List<Blockchain<TendermintTransaction>> getAllAgentBlockchain(List<SimpleAgent> agents) {
        List<Blockchain<TendermintTransaction>> allBlockchains = Lists.newArrayList();
        for (SimpleAgent agent : agents) {
            if (isTendermintValidator(agent)) {
                Tendermint tendermint = agent.getProtocol(Tendermint.class);
                Blockchain<TendermintTransaction> blockchain = tendermint.getDecision();
                allBlockchains.add(blockchain);
            }
        }
        return allBlockchains;
    }

    private void displayBlockchain() {
        displayLogTitle("BLOCKCHAIN");

        List<SimpleAgent> agents = PalmBeachSimulation.allAgents();
        List<Blockchain<TendermintTransaction>> allBlockchains = getAllAgentBlockchain(agents);
        Blockchain<TendermintTransaction> blockchain = allBlockchains.get(0);

        for (int i = 0; i <= blockchain.currentHeight(); i++) {
            displayBlock(blockchain.getBlock(i));
            displayArrow();
        }
    }

    private void displayBlock(Block<TendermintTransaction> block) {
        displayHorizontalSeparator();
        displayLine("  Block %d, Timestamp %d, Previous %s".formatted(block.getHeight(), block.getTimestamp(), block.getPrevious()));
        displayLine("  SHA256: %s".formatted(block.sha256Base64Hash()));
        displayHorizontalSeparator();
        for (TendermintTransaction tx : block.getTransactions()) {
            displayLine("  T:%d, S: %s, R:%s, Amount: %d".formatted(tx.getTimestamp(), tx.getSender(), tx.getReceiver(), tx.getAmount()));
        }
        displayHorizontalSeparator();
    }

    private void displayHorizontalSeparator() {
        StringBuilder builder = new StringBuilder("+");
        builder.append("-".repeat(DISPLAY_BLOCK_WIDTH));
        builder.append("+");
        log.info("{}", builder);
    }

    private void displayLine(String content) {
        StringBuilder builder = new StringBuilder("|");
        builder.append(content);
        builder.append(" ".repeat(DISPLAY_BLOCK_WIDTH - content.length()));
        builder.append("|");
        log.info("{}", builder);
    }

    private void displayArrow() {
        StringBuilder builder = new StringBuilder();
        builder.append(" ".repeat(DISPLAY_BLOCK_WIDTH / 2));
        builder.append("^");
        builder.append(" ".repeat(DISPLAY_BLOCK_WIDTH / 2));
        log.info("{}", builder);
        builder = new StringBuilder();
        builder.append(" ".repeat(DISPLAY_BLOCK_WIDTH / 2));
        builder.append("|");
        builder.append(" ".repeat(DISPLAY_BLOCK_WIDTH / 2));
        log.info("{}", builder);
    }

    private boolean isTendermintValidator(SimpleAgent agent) {
        return agent.getProtocol(Tendermint.class) != null;
    }

    private void displayLogTitle(String title) {
        log.info("---------------------------------------------------------------");
        log.info("{}", title.toUpperCase());
        log.info("---------------------------------------------------------------");
    }

    // Inner classes.

    private static record Pair<T, S>(T first, S second) {
    }
}
