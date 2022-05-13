package org.paradise.simulation.tendermint.client;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.basic.messaging.broadcasting.Broadcaster;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.agent.protocol.Protocol;
import org.paradise.palmbeach.core.environment.network.Network;
import org.paradise.palmbeach.core.event.Event;
import org.paradise.palmbeach.core.scheduler.executor.Executable;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.utils.context.Context;
import org.paradise.palmbeach.utils.validation.Validate;
import org.paradise.simulation.tendermint.client.message.TendermintTransactionMessage;
import org.paradise.simulation.tendermint.validator.TendermintValidator;
import org.paradise.simulation.tendermint.validator.TendermintTransaction;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class TendermintClient extends Protocol {

    // Constants.

    public static final String TENDERMINT_CLIENT_NAME_PREFIX = "TendermintClient";

    // Context

    public static final String MIN_TIME_BETWEEN_SENDING = "minTimeBetweenSending";
    public static final int DEFAULT_MIN_TIME_BETWEEN_SENDING = 150;

    public static final String MAX_TIME_BETWEEN_SENDING = "maxTimeBetweenSending";
    public static final int DEFAULT_MAX_TIME_BETWEEN_SENDING = 250;

    public static final String NB_SENDING_TX = "nbSendingTxTime";
    public static final int DEFAULT_NB_SENDING_TX = 500;

    public static final String NB_CONNECTION_TO_VALIDATOR = "nbConnectionToValidator";
    public static final int DEFAULT_NB_CONNECTION_TO_VALIDATOR = 3;

    public static final String MIN_TX_CREATED = "minTxCreated";
    public static final int DEFAULT_MIN_TX_CREATED = 50;

    public static final String MAX_TX_CREATED = "maxTxCreated";
    public static final int DEFAULT_MAX_TX_CREATED = 150;

    // Variables.

    private final Random random = new Random();

    private final Set<SimpleAgent.AgentIdentifier> connectedValidators;

    private int sendingCounter = 0;

    @Setter
    private Broadcaster broadcaster;

    @Setter
    private Network network;

    @Getter
    private final Set<TendermintTransaction> transactionSent;

    // Constructors.

    public TendermintClient(@NonNull SimpleAgent agent, Context context) {
        super(agent, context);
        this.connectedValidators = Sets.newHashSet();
        this.transactionSent = Sets.newHashSet();
    }

    // Methods.

    @Override
    protected ProtocolManipulator defaultProtocolManipulator() {
        return new DefaultProtocolManipulator(this);
    }

    @Override
    public void agentStarted() {
        fillConnectedValidators();
        scheduleNextSending();
    }

    private void scheduleNextSending() {
        PalmBeachSimulation.scheduler().scheduleOnce(new SendingTxExecutable(), random.nextInt(minTimeBetweenSending(), maxTimeBetweenSending() + 1));
    }

    private void fillConnectedValidators() {
        connectedValidators.clear();
        List<SimpleAgent.AgentIdentifier> tendermintAgents =
                PalmBeachSimulation.allAgents().stream().map(agent -> (SimpleAgent.SimpleAgentIdentifier) agent.getIdentifier())
                        .filter(identifier -> identifier.getAgentName().contains(TendermintValidator.TENDERMINT_VALIDATOR_AGENT_NAME_PREFIX))
                        .map(identifier -> (SimpleAgent.AgentIdentifier) identifier).collect(Collectors.toList());

        Collections.shuffle(tendermintAgents);

        for (int i = 0; i < tendermintAgents.size() && i < nbConnectionToValidator(); i++) {
            connectedValidators.add(tendermintAgents.get(i));
        }
    }

    @Override
    public void agentStopped() {
        // Nothing
    }

    @Override
    public void agentKilled() {
        // Nothing
    }

    @Override
    public void processEvent(Event<?> event) {
        // Nothing
    }

    @Override
    public boolean canProcessEvent(Event<?> event) {
        return false;
    }

    // Getters and Setters.

    public int minTimeBetweenSending() {
        return getContext().getInt(MIN_TIME_BETWEEN_SENDING, DEFAULT_MIN_TIME_BETWEEN_SENDING,
                                   new Validate.MinIntValidator(1, "MinTimeBetweenSending " +
                                           "cannot be less than 1"));
    }

    @SuppressWarnings("unused")
    public void minTimeBetweenSending(int minTimeBetweenSending) {
        getContext().setInt(MIN_TIME_BETWEEN_SENDING, minTimeBetweenSending,
                            new Validate.MinIntValidator(1, "MinTimeBetweenSending cannot be less than 1"));
    }

    public int maxTimeBetweenSending() {
        return getContext().getInt(MAX_TIME_BETWEEN_SENDING, DEFAULT_MAX_TIME_BETWEEN_SENDING, new Validate.MinIntValidator(1,
                                                                                                                            "MaxTimeBetweenSending cannot be less than 1"));
    }

    @SuppressWarnings("unused")
    public void maxTimeBetweenSending(int maxTimeBetweenSending) {
        getContext().setInt(MAX_TIME_BETWEEN_SENDING, maxTimeBetweenSending,
                            new Validate.MinIntValidator(1, "MaxTimeBetweenSending cannot be less than 1"));
    }

    public int nbSendingTx() {
        return getContext().getInt(NB_SENDING_TX, DEFAULT_NB_SENDING_TX, new Validate.MinIntValidator(1, "NbSendingTx cannot be less than 1"));
    }

    @SuppressWarnings("unused")
    public void nbSendingTx(int nbSendingTx) {
        getContext().setInt(NB_SENDING_TX, nbSendingTx, new Validate.MinIntValidator(1, "NbSendingTx cannot be less than 1"));
    }

    public int nbConnectionToValidator() {
        return getContext().getInt(NB_CONNECTION_TO_VALIDATOR, DEFAULT_NB_CONNECTION_TO_VALIDATOR, new Validate.MinIntValidator(1,
                                                                                                                                "NbConnectionToValidator cannot be less than 1"));
    }

    @SuppressWarnings("unused")
    public void nbConnectionToValidator(int nbConnectionToValidator) {
        getContext().setInt(NB_CONNECTION_TO_VALIDATOR, nbConnectionToValidator,
                            new Validate.MinIntValidator(1, "NbConnectionToValidator cannot be less than 1"));
    }

    public int minTxCreated() {
        return getContext().getInt(MIN_TX_CREATED, DEFAULT_MIN_TX_CREATED, new Validate.MinIntValidator(1, "MinTxCreated cannot be less than 1"));
    }

    @SuppressWarnings("unused")
    public void minTxCreated(int minTxCreated) {
        getContext().setInt(MIN_TX_CREATED, minTxCreated, new Validate.MinIntValidator(1, "MinTxCreated cannot be less than 1"));
    }

    public int maxTxCreated() {
        return getContext().getInt(MAX_TX_CREATED, DEFAULT_MAX_TX_CREATED, new Validate.MinIntValidator(1, "MaxTxCreated cannot be less than 1"));
    }

    @SuppressWarnings("unused")
    public void maxTxCreated(int maxTxCreated) {
        getContext().setInt(MAX_TX_CREATED, maxTxCreated, new Validate.MinIntValidator(1, "MaxTxCreated cannot be less than 1"));
    }

    // Inner classes.

    private class SendingTxExecutable implements Executable {

        @Override
        public void execute() {
            if (sendingCounter < nbSendingTx()) {
                sendTx();
                scheduleNextSending();
                sendingCounter++;
            }
        }

        @Override
        public Object getLockMonitor() {
            return getAgent();
        }

        private void sendTx() {
            log.info("{} sendTx for the {}th time", getAgent().getIdentifier(), sendingCounter);
            for (int i = 0; i < random.nextInt(minTxCreated(), maxTxCreated() + 1); i++) {
                String sender = getAgent().getIdentifier().toString();
                String receiver = String.valueOf(random.nextInt(5000));
                TendermintTransaction tx = new TendermintTransaction(PalmBeachSimulation.scheduler().getCurrentTime(), sender, receiver,
                                                                     random.nextLong(Long.MAX_VALUE));
                sendToValidators(tx);
                transactionSent.add(tx);
            }
        }

        private void sendToValidators(TendermintTransaction tx) {
            broadcaster.broadcastMessage(new TendermintTransactionMessage(tx), connectedValidators, network);
        }
    }
}
