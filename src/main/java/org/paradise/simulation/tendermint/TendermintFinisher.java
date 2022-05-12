package org.paradise.simulation.tendermint;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.blockchain.block.Block;
import org.paradise.palmbeach.blockchain.block.Blockchain;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.core.simulation.SimulationFinisher;
import org.paradise.simulation.tendermint.client.TendermintClient;
import org.paradise.simulation.tendermint.validator.Tendermint;
import org.paradise.simulation.tendermint.validator.TendermintTransaction;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class TendermintFinisher implements SimulationFinisher {

    private static double begin = -1.d;

    private static final int DISPLAY_BLOCK_WIDTH = 115;

    @Override

    public void finishSimulation() {
        displayBlockchain();
        displayDuration();
        displayGSTAverage();
        displayBlockchainVerification();
        displayBlockchainData();
        displayTransactionVerification();
        displayVerifyDoublonInBC();
        displayBlockFill();
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
        List<Blockchain<TendermintTransaction>> allBlockchains = getAllBlockchains(agents);

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

    private void displayBlockchain() {
        displayLogTitle("BLOCKCHAIN");

        List<SimpleAgent> agents = PalmBeachSimulation.allAgents();
        List<Blockchain<TendermintTransaction>> allBlockchains = getAllBlockchains(agents);
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
        displayLine("  Size: %d".formatted(block.getTransactions().size()));
        displayHorizontalSeparator();
    }

    @SuppressWarnings("unused")
    private void displayTx(Block<TendermintTransaction> block) {
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

    private void displayBlockchainData() {
        displayLogTitle("Blockchain data");

        List<SimpleAgent> agents = PalmBeachSimulation.allAgents();
        List<Blockchain<TendermintTransaction>> allBlockchains = getAllBlockchains(agents);

        Pair<Long, Long> blockCreationTimeMinMax = blockCreationTimeMinMax(allBlockchains.get(0));
        double blockCreationTimeAverage = computeBlockCreationTimeAverage(allBlockchains.get(0));

        log.info("Min creation time = {}", blockCreationTimeMinMax.first());
        log.info("Max creation time = {}", blockCreationTimeMinMax.second());
        log.info("Average block creation time = {}", blockCreationTimeAverage);

        SimpleAgent validator = randomValidator(agents);
        Tendermint tendermint = validator.getProtocol(Tendermint.class);
        Map<Long, Long> heightRound = tendermint.getMapHeightRound();
        long minNbRound = Long.MAX_VALUE;
        long maxNbRound = Long.MIN_VALUE;
        double roundAverage = 0.d;

        for (Map.Entry<Long, Long> entry : heightRound.entrySet()) {
            long r = entry.getValue();

            if (minNbRound > r) {
                minNbRound = r;
            }

            if (maxNbRound < r) {
                maxNbRound = r;
            }

            roundAverage += r;
        }

        roundAverage = roundAverage / heightRound.size();

        log.info("Height = {}", heightRound.size());
        log.info("Min round = {}", minNbRound);
        log.info("Max round = {}", maxNbRound);
        log.info("Average round = {}", roundAverage);
    }

    private Pair<Long, Long> blockCreationTimeMinMax(Blockchain<TendermintTransaction> bc) {
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (long i = 2; i <= bc.currentHeight(); i++) {
            long timeBlockCreation = bc.getBlock(i).getTimestamp() - bc.getBlock(i - 1L).getTimestamp();
            if (min > timeBlockCreation) {
                min = timeBlockCreation;
            }

            if (max < timeBlockCreation) {
                max = timeBlockCreation;
            }
        }

        return new Pair<>(min, max);
    }

    private double computeBlockCreationTimeAverage(Blockchain<TendermintTransaction> bc) {
        double blockCreationTimeAverage = 0L;
        for (long i = 2L; i <= bc.currentHeight(); i++) {
            long timeBlockCreation = bc.getBlock(i).getTimestamp() - bc.getBlock(i - 1L).getTimestamp();
            blockCreationTimeAverage += timeBlockCreation;
        }

        return blockCreationTimeAverage / (bc.currentHeight() - 1);
    }

    private void displayTransactionVerification() {
        displayLogTitle("Transaction Verification");

        Set<TendermintTransaction> allTransactionSent = getAllTransactionSent();

        boolean allInBlockchain = true;

        List<Blockchain<TendermintTransaction>> allBlockchains = getAllBlockchains(PalmBeachSimulation.allAgents());
        for (Blockchain<TendermintTransaction> bc : allBlockchains) {
            Set<TendermintTransaction> bcTx = allTxInBc(bc);
            if (!bcTx.containsAll(allTransactionSent)) {
                allInBlockchain = false;
                log.error("A Blockchain does not contain all transactions sent");
            }
        }

        log.info("Total transaction sent: {}", allTransactionSent.size());
        log.info("All transactions sent are in the blockchain {}", allInBlockchain);
    }

    private Set<TendermintTransaction> allTxInBc(Blockchain<TendermintTransaction> bc) {
        Set<TendermintTransaction> allTx = Sets.newHashSet();
        for (Block<TendermintTransaction> block : bc) {
            allTx.addAll(block.getTransactions());
        }
        return allTx;
    }

    private Set<TendermintTransaction> getAllTransactionSent() {
        Set<TendermintTransaction> allTransactionSent = Sets.newHashSet();
        List<SimpleAgent> clientAgents = PalmBeachSimulation.allAgents().stream().filter(this::isTendermintClient).toList();

        for (SimpleAgent agent : clientAgents) {
            TendermintClient tendermintClient = agent.getProtocol(TendermintClient.class);
            allTransactionSent.addAll(tendermintClient.getTransactionSent());
        }

        return allTransactionSent;
    }

    private void displayVerifyDoublonInBC() {
        displayLogTitle("Doublon verification");

        List<SimpleAgent> agents = PalmBeachSimulation.allAgents();
        List<Blockchain<TendermintTransaction>> allBlockchains = getAllBlockchains(agents);
        Blockchain<TendermintTransaction> bc = allBlockchains.get(0);

        Set<TendermintTransaction> allTx = Sets.newHashSet();
        long nbDoublon = 0L;
        long totalTxInBc = 0L;

        for (Block<TendermintTransaction> block : bc) {
            for (TendermintTransaction tx : block.getTransactions()) {
                if (!allTx.add(tx)) {
                    nbDoublon++;
                }
            }
            totalTxInBc += block.getTransactions().size();
        }

        log.info("Nb doublon = {}", nbDoublon);
        log.info("Total tx in Blockchain = {}", totalTxInBc);
    }

    private void displayBlockFill() {
        displayLogTitle("Block fill");

        List<SimpleAgent> agents = PalmBeachSimulation.allAgents();
        List<Blockchain<TendermintTransaction>> allBlockchains = getAllBlockchains(agents);
        Blockchain<TendermintTransaction> bc = allBlockchains.get(0);

        long minSize = Long.MAX_VALUE;
        long maxSize = Long.MIN_VALUE;
        double sizeAverage = 0.d;

        for (Block<TendermintTransaction> block : bc) {
            Set<TendermintTransaction> txs = block.getTransactions();
            if (minSize > txs.size()) {
                minSize = txs.size();
            }

            if (maxSize < txs.size()) {
                maxSize = txs.size();
            }

            sizeAverage += txs.size();
        }

        sizeAverage = sizeAverage / (bc.currentHeight() - 1);

        log.info("Block size min = {}", minSize);
        log.info("Block size max = {}", maxSize);
        log.info("Block size average = {}", sizeAverage);
    }

    private List<Blockchain<TendermintTransaction>> getAllBlockchains(List<SimpleAgent> agents) {
        List<Blockchain<TendermintTransaction>> allBlockchains = Lists.newArrayList();
        for (SimpleAgent agent : agents) {
            if (isTendermintValidator(agent)) {
                Tendermint tendermint = agent.getProtocol(Tendermint.class);
                Blockchain<TendermintTransaction> blockchain = tendermint.getDecision();
                allBlockchains.add(blockchain);
            }
        }
        Collections.shuffle(allBlockchains);
        return allBlockchains;
    }

    private SimpleAgent randomValidator(List<SimpleAgent> agents) {
        List<SimpleAgent> allAgents = Lists.newArrayList(agents);
        Collections.shuffle(allAgents);
        for (SimpleAgent agent : allAgents) {
            if (isTendermintValidator(agent)) {
                return agent;
            }
        }
        return null;
    }

    private void displayLogTitle(String title) {
        log.info("---------------------------------------------------------------");
        log.info("{}", title.toUpperCase());
        log.info("---------------------------------------------------------------");
    }

    private boolean isTendermintValidator(SimpleAgent agent) {
        return agent.getProtocol(Tendermint.class) != null;
    }

    private boolean isTendermintClient(SimpleAgent agent) {
        return agent.getProtocol(TendermintClient.class) != null;
    }

    // Inner classes.

    private static record Pair<T, S>(T first, S second) {
    }
}
