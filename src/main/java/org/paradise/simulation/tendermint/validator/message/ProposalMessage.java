package org.paradise.simulation.tendermint.validator.message;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.paradise.palmbeach.blockchain.block.Block;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.simulation.tendermint.validator.TendermintValidator;
import org.paradise.simulation.tendermint.validator.TendermintTransaction;

@EqualsAndHashCode(callSuper = true)
public class ProposalMessage extends TendermintMessage<Block<TendermintTransaction>> {

    // Variables.

    @Getter
    private final long validRound;

    // Constructors.

    public ProposalMessage(@NonNull SimpleAgent.AgentIdentifier sender, long height, long round, Block<TendermintTransaction> proposal,
                           long validRound) {
        super(sender, height, round, proposal);
        this.validRound = validRound;
    }

    // Methods.

    public TendermintValidator.Proposal getProposal() {
        return new TendermintValidator.Proposal(getHeight(), getRound(), getContent(), getValidRound());
    }
}
