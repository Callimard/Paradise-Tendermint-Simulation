package org.paradise.simulation.tendermint;

import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.basic.messaging.broadcasting.Broadcaster;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.agent.protocol.Protocol;
import org.paradise.palmbeach.core.environment.network.Network;
import org.paradise.palmbeach.utils.context.Context;
import org.paradise.simulation.tendermint.seed.NodeDirectory;
import org.paradise.simulation.tendermint.seed.NodeRegistration;
import org.paradise.simulation.tendermint.seed.message.NodeRegistrationMessage;

import java.util.Set;

@Slf4j
public abstract class TendermintProtocol extends Protocol {

    // Variables.

    @Getter
    private final Set<SimpleAgent.AgentIdentifier> seedNodes;

    @Getter
    private final Set<SimpleAgent.AgentIdentifier> validators;

    @Getter
    private final Set<SimpleAgent.AgentIdentifier> fullNodes;

    @Getter
    @Setter
    private Network network;

    @Getter
    @Setter
    private Broadcaster broadcaster;

    // Constructors.

    protected TendermintProtocol(@NonNull SimpleAgent agent, Context context) {
        super(agent, context);

        this.seedNodes = Sets.newHashSet();
        this.validators = Sets.newHashSet();
        this.fullNodes = Sets.newHashSet();
    }

    // Methods.

    protected ProtocolManipulator defaultProtocolManipulator() {
        return new DefaultProtocolManipulator(this);
    }

    protected void registerToSeedNode(NodeType nodeType) {
        SimpleAgent.AgentIdentifier agent = getAgent().getIdentifier();
        broadcaster.broadcastMessage(new NodeRegistrationMessage(agent, new NodeRegistration(agent, nodeType)), getSeedNodes(),
                                     getNetwork());
    }

    protected void updateNodeDirectory(NodeDirectory nodeDirectory) {
        log.info("{} Update NodeDirectory {}", getAgent().getIdentifier(), nodeDirectory);
        Set<SimpleAgent.AgentIdentifier> seedNodesReceived = nodeDirectory.seedNodes();
        Set<SimpleAgent.AgentIdentifier> validatorsReceived = nodeDirectory.validators();
        Set<SimpleAgent.AgentIdentifier> fullNodesReceived = nodeDirectory.fullNodes();

        updateSeedNodes(seedNodesReceived);
        updateValidators(validatorsReceived);
        updateFullNodes(fullNodesReceived);
    }

    private void updateSeedNodes(Set<SimpleAgent.AgentIdentifier> seedNodesReceived) {
        seedNodes.addAll(seedNodesReceived);
    }

    private void updateValidators(Set<SimpleAgent.AgentIdentifier> validatorsReceived) {
        validators.addAll(validatorsReceived);
    }

    private void updateFullNodes(Set<SimpleAgent.AgentIdentifier> fullNodesReceived) {
        fullNodes.addAll(fullNodesReceived);
    }
}
