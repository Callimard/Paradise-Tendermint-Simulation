package org.paradise.simulation.tendermint.client;

import com.google.common.collect.Sets;
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
import org.paradise.simulation.tendermint.client.message.TendermintTransactionMessage;
import org.paradise.simulation.tendermint.validator.Tendermint;
import org.paradise.simulation.tendermint.validator.TendermintTransaction;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.paradise.palmbeach.utils.validation.Validate.min;

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

    // Constructors.

    public TendermintClient(@NonNull SimpleAgent agent, Context context) {
        super(agent, context);
        this.connectedValidators = Sets.newHashSet();
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
                        .filter(identifier -> identifier.getAgentName().contains(Tendermint.TENDERMINT_AGENT_NAME_PREFIX))
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
        if (getContext().hasValue(MIN_TIME_BETWEEN_SENDING)) {
            int minTimeBetweenSending = getContext().getInt(MIN_TIME_BETWEEN_SENDING);
            min(minTimeBetweenSending, 1, "MinTimeBetweenSending cannot be less than 1");
            return minTimeBetweenSending;
        } else
            return DEFAULT_MIN_TIME_BETWEEN_SENDING;
    }

    @SuppressWarnings("unused")
    public void minTimeBetweenSending(int minTimeBetweenSending) {
        min(minTimeBetweenSending, 1, "MinTimeBetweenSending cannot be less than 1");
        getContext().setInt(MIN_TIME_BETWEEN_SENDING, minTimeBetweenSending);
    }

    public int maxTimeBetweenSending() {
        if (getContext().hasValue(MAX_TIME_BETWEEN_SENDING)) {
            int maxTimeBetweenSending = getContext().getInt(MAX_TIME_BETWEEN_SENDING);
            min(maxTimeBetweenSending, 1, "MaxTimeBetweenSending cannot be less than 1");
            return maxTimeBetweenSending;
        } else
            return DEFAULT_MAX_TIME_BETWEEN_SENDING;
    }

    @SuppressWarnings("unused")
    public void maxTimeBetweenSending(int maxTimeBetweenSending) {
        min(maxTimeBetweenSending, 1, "MaxTimeBetweenSending cannot be less than 1");
        getContext().setInt(MAX_TIME_BETWEEN_SENDING, maxTimeBetweenSending);
    }

    public int nbSendingTx() {
        if (getContext().hasValue(NB_SENDING_TX)) {
            int nbSendingTx = getContext().getInt(NB_SENDING_TX);
            min(nbSendingTx, 1, "NbSendingTx cannot be less than 1");
            return nbSendingTx;
        } else
            return DEFAULT_NB_SENDING_TX;
    }

    @SuppressWarnings("unused")
    public void nbSendingTx(int nbSendingTx) {
        min(nbSendingTx, 1, "NbSendingTx cannot be less than 1");
        getContext().setInt(NB_SENDING_TX, nbSendingTx);
    }

    public int nbConnectionToValidator() {
        if (getContext().hasValue(NB_CONNECTION_TO_VALIDATOR)) {
            int nbConnectionToValidator = getContext().getInt(NB_CONNECTION_TO_VALIDATOR);
            min(nbConnectionToValidator, 1, "NbConnectionToValidator cannot be less than 1");
            return nbConnectionToValidator;
        } else
            return DEFAULT_NB_CONNECTION_TO_VALIDATOR;
    }

    @SuppressWarnings("unused")
    public void nbConnectionToValidator(int nbConnectionToValidator) {
        min(nbConnectionToValidator, 1, "NbConnectionToValidator cannot be less than 1");
        getContext().setInt(NB_CONNECTION_TO_VALIDATOR, nbConnectionToValidator);
    }

    public int minTxCreated() {
        if (getContext().hasValue(MIN_TX_CREATED)) {
            int minTxCreated = getContext().getInt(MIN_TX_CREATED);
            min(minTxCreated, 1, "MinTxCreated cannot be less than 1");
            return minTxCreated;
        } else
            return DEFAULT_MIN_TX_CREATED;
    }

    @SuppressWarnings("unused")
    public void minTxCreated(int minTxCreated) {
        min(minTxCreated, 1, "MinTxCreated cannot be less than 1");
        getContext().setInt(MIN_TX_CREATED, minTxCreated);
    }

    public int maxTxCreated() {
        if (getContext().hasValue(MAX_TX_CREATED)) {
            int maxTxCreated = getContext().getInt(MAX_TX_CREATED);
            min(maxTxCreated, 1, "MaxTxCreated cannot be less than 1");
            return maxTxCreated;
        } else
            return DEFAULT_MAX_TX_CREATED;
    }

    @SuppressWarnings("unused")
    public void maxTxCreated(int maxTxCreated) {
        min(maxTxCreated, 1, "MaxTxCreated cannot be less than 1");
        getContext().setInt(MAX_TX_CREATED, maxTxCreated);
    }

    // Inner classes.

    private class SendingTxExecutable implements Executable {

        @Override
        public void execute() {
            sendTx();
            if (sendingCounter++ < nbSendingTx()) {
                scheduleNextSending();
            }
        }

        @Override
        public Object getLockMonitor() {
            return getAgent();
        }

        private void sendTx() {
            log.info("{} sendTx for the {}th time", getAgent().getIdentifier(), sendingCounter + 1);
            for (int i = 0; i < random.nextInt(minTxCreated(), maxTxCreated()); i++) {
                String sender = getAgent().getIdentifier().toString();
                String receiver = String.valueOf(random.nextInt(5000));
                sendToValidators(
                        new TendermintTransaction(PalmBeachSimulation.scheduler().getCurrentTime(), sender, receiver,
                                                  random.nextLong(Long.MAX_VALUE)));
            }
        }

        private void sendToValidators(TendermintTransaction tx) {
            broadcaster.broadcastMessage(new TendermintTransactionMessage(tx), connectedValidators, network);
        }
    }
}
