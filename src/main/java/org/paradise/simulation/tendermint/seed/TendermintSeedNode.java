package org.paradise.simulation.tendermint.seed;

import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.basic.messaging.MessageReceiver;
import org.paradise.palmbeach.basic.messaging.broadcasting.Broadcaster;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.event.Event;
import org.paradise.palmbeach.utils.context.Context;
import org.paradise.simulation.tendermint.NodeType;
import org.paradise.simulation.tendermint.TendermintProtocol;
import org.paradise.simulation.tendermint.seed.message.GetNodeDirectoryMessage;
import org.paradise.simulation.tendermint.seed.message.NodeDirectoryMessage;
import org.paradise.simulation.tendermint.seed.message.NodeRegistrationMessage;
import org.paradise.simulation.tendermint.seed.message.TendermintSeedNodeMessage;

@Slf4j
public class TendermintSeedNode extends TendermintProtocol implements MessageReceiver.MessageReceiverObserver {

    // Constants.

    public static final String ROOT_SEED_NODE_PREFIX = "RootSeedNode";
    public static final String UNKNOWN_SEED_NODE_PREFIX = "UnknownSeedNode";

    // Constructors.

    public TendermintSeedNode(@NonNull SimpleAgent agent, Context context) {
        super(agent, context);
    }

    // Methods.

    @Override
    public void agentStarted() {
        registerToSeedNode(NodeType.SEED_NODE);
    }

    @Override
    public void agentStopped() {
        // Nothing
    }

    @Override
    public void agentKilled() {
        // Nothing.
    }

    @Override
    public void processEvent(Event<?> event) {
        // Nothing
    }

    @Override
    public boolean canProcessEvent(Event<?> event) {
        return false;
    }

    @Override
    public void messageDelivery(@NonNull MessageReceiver messageReceiver, Object o) {
        if (o instanceof NodeRegistrationMessage nodeRegistrationMsg) {
            treatNodeRegistrationMessage(nodeRegistrationMsg);
        } else if (o instanceof GetNodeDirectoryMessage getNodeDirectoryMsg) {
            treatNodeDirectoryDemand(getNodeDirectoryMsg);
        } else if (o instanceof NodeDirectoryMessage nodeDirectoryMsg) {
            treatNodeDirectoryReception(nodeDirectoryMsg);
        } else
            log.error("Cannot treat object received {}", o.getClass());
    }

    private void treatNodeRegistrationMessage(NodeRegistrationMessage nodeRegistrationMsg) {
        if (validRegistration(nodeRegistrationMsg)) {
            boolean added = true;
            final NodeRegistration registration = nodeRegistrationMsg.getContent();
            switch (registration.nodeType()) {
                case VALIDATOR -> added = getValidators().add(registration.agent());
                case SEED_NODE -> added = getSeedNodes().add(registration.agent());
                case FULL_NODE -> added = getFullNodes().add(registration.agent());
            }

            if (added)
                log.debug("{} SeedNode add the agent {} as {}", getAgent().getIdentifier(), registration.agent(), registration.nodeType());
            else
                log.debug("{} SeedNode has already added the agent {} as {}", getAgent().getIdentifier(), registration.agent(),
                          registration.nodeType());
        } else
            log.debug("{} SeedNode received invalid node registration msg {}", getAgent().getIdentifier(), nodeRegistrationMsg);
    }

    private boolean validRegistration(NodeRegistrationMessage nodeRegistrationMsg) {
        return nodeRegistrationMsg.getSender().equals(nodeRegistrationMsg.getContent().agent());
    }

    private void treatNodeDirectoryDemand(GetNodeDirectoryMessage getNodeDirectoryMsg) {
        NodeDirectory nodeDirectory = new NodeDirectory(getSeedNodes(), getValidators(), getFullNodes());
        broadcastNodeDirectory(getNodeDirectoryMsg.getSender(), nodeDirectory);
    }

    private void broadcastNodeDirectory(SimpleAgent.AgentIdentifier demander, NodeDirectory nodeDirectory) {
        getBroadcaster().broadcastMessage(new NodeDirectoryMessage(getAgent().getIdentifier(), nodeDirectory),
                                          Sets.newHashSet(demander), getNetwork());
    }

    private void treatNodeDirectoryReception(NodeDirectoryMessage nodeDirectoryMsg) {
        updateNodeDirectory(nodeDirectoryMsg.getContent());
    }

    @Override
    public boolean interestedBy(Object o) {
        return o instanceof TendermintSeedNodeMessage;
    }

    // Getters and Setters.

    @SuppressWarnings("unused")
    @Override
    public void setBroadcaster(Broadcaster broadcaster) {
        super.setBroadcaster(broadcaster);
        getBroadcaster().addObserver(this);
    }
}
