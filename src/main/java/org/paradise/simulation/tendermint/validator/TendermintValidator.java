package org.paradise.simulation.tendermint.validator;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.paradise.palmbeach.basic.messaging.MessageReceiver;
import org.paradise.palmbeach.basic.messaging.broadcasting.Broadcaster;
import org.paradise.palmbeach.blockchain.block.Block;
import org.paradise.palmbeach.blockchain.block.Blockchain;
import org.paradise.palmbeach.blockchain.block.NonForkBlockchain;
import org.paradise.palmbeach.core.agent.SimpleAgent;
import org.paradise.palmbeach.core.agent.exception.AgentNotStartedException;
import org.paradise.palmbeach.core.event.Event;
import org.paradise.palmbeach.core.simulation.PalmBeachSimulation;
import org.paradise.palmbeach.utils.context.Context;
import org.paradise.palmbeach.utils.validation.Validate;
import org.paradise.simulation.tendermint.TendermintProtocol;
import org.paradise.simulation.tendermint.client.message.TendermintClientMessage;
import org.paradise.simulation.tendermint.client.message.TransactionMessage;
import org.paradise.simulation.tendermint.seed.message.NodeDirectoryMessage;
import org.paradise.simulation.tendermint.transaction.TendermintTransaction;
import org.paradise.simulation.tendermint.transaction.money.TendermintLockStakeTx;
import org.paradise.simulation.tendermint.transaction.money.TendermintUnlockStakeTx;
import org.paradise.simulation.tendermint.validator.message.PrecommitMessage;
import org.paradise.simulation.tendermint.validator.message.PrevoteMessage;
import org.paradise.simulation.tendermint.validator.message.ProposalMessage;
import org.paradise.simulation.tendermint.validator.message.TendermintValidatorMessage;
import org.paradise.simulation.tendermint.validator.pos.CommitteeSelector;
import org.paradise.simulation.tendermint.validator.pos.CommitteeSelectorFactory;
import org.paradise.simulation.tendermint.validator.pos.ProofOfStakeState;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.paradise.palmbeach.core.simulation.PalmBeachSimulation.scheduleEvent;

@Slf4j
public class TendermintValidator extends TendermintProtocol implements MessageReceiver.MessageReceiverObserver {

    // Constants.

    public static final String TENDERMINT_VALIDATOR_AGENT_NAME_PREFIX = "TendermintValidator";

    // Context.

    public static final String BLOCKCHAIN = "tendermintBlockchain";

    public static final String MAX_HEIGHT = "maxHeight";
    public static final long DEFAULT_MAX_HEIGHT = 100;

    public static final String MAX_BLOCK_SIZE = "maxBlockSize";
    public static final int DEFAULT_MAX_BLOCK_SIZE = 50;

    public static final String COMMITTEE_SIZE = "committeeSize";
    public static final int DEFAULT_COMMITTEE_SIZE = 7;

    // Variables.

    @Getter
    private final String address;

    private long round = 0L;
    private Step step = Step.PROPOSE;

    @Getter
    @Setter
    @NonNull
    private Blockchain<TendermintTransaction> decision;

    private Block<TendermintTransaction> lockedValue;
    private long lockRound = -1;
    private Block<TendermintTransaction> validValue;
    private long validRound = -1;

    @Getter
    private long timeoutPropose;
    @Getter
    private long timeoutPrevote;
    @Getter
    private long timeoutPrecommit;
    private long deltaTimeout;

    private final Set<Proposal> proposalReceived = Sets.newHashSet();
    private final Map<Stage, Set<SimpleAgent.AgentIdentifier>> prevoteReceived = Maps.newHashMap();
    private final Map<Prevote, Long> prevoteCounter = Maps.newHashMap();
    private final Map<Stage, Set<SimpleAgent.AgentIdentifier>> precommitReceived = Maps.newHashMap();
    private final Map<Precommit, Long> precommitCounter = Maps.newHashMap();

    private final Map<Rule, Set<Stage>> ruleAlreadyExecuted = Maps.newHashMap();

    private final Queue<TendermintTransaction> memoryPool;
    private final Set<TendermintTransaction> setMemoryPool;

    private final Set<TendermintTransaction> polledTx;

    private final Set<SimpleAgent.AgentIdentifier> groupMembership;
    private Set<String> currentCommittee;

    private final Set<Rule> rules = Sets.newHashSet(new ProposalForRound(), new ProposalAndPrevoteForValidRound(), new PrevoteForRound(),
                                                    new ProposalAndPrevoteForRound(), new PrevoteNilForRound(), new PrecommitForRound(),
                                                    new ProposalAndPrecommitForRound(), new ChangeRound());

    private final ProofOfStakeState posState;

    // Analyse

    @Getter
    private final Map<Long, Long> mapHeightRound;

    // Constructors.

    public TendermintValidator(@NonNull SimpleAgent agent, Context context) {
        super(agent, context);
        this.address = generateAddress();
        this.decision = new NonForkBlockchain<>(new Block<>(Block.GENESIS_BLOCK_HEIGHT, Block.GENESIS_BLOCK_TIMESTAMP, Block.GENESIS_BLOCK_PREVIOUS,
                                                            Sets.newHashSet()));
        initTimeout();
        getContext().map(BLOCKCHAIN, this.decision);
        this.memoryPool = Lists.newLinkedList();
        this.setMemoryPool = Sets.newHashSet();
        this.polledTx = Sets.newHashSet();
        this.mapHeightRound = Maps.newHashMap();
        this.groupMembership = Sets.newHashSet();
        this.posState = new ProofOfStakeState();
    }

    private String generateAddress() {
        SimpleAgent.AgentIdentifier identifier = getAgent().getIdentifier();
        String concatIdentifier = identifier.getAgentName() + identifier.getUniqueId();

        return encodeBase64String(concatIdentifier.getBytes(StandardCharsets.UTF_8));
    }

    public void reComputePoSState() {
        posState.clear();
        for (int i = 0; i <= decision.currentHeight(); i++) {
            updatePoSState(decision.getBlock(i));
        }
    }

    private void updatePoSState(Block<TendermintTransaction> block) {
        for (TendermintTransaction tx : block.getTransactions()) {
            if (tx instanceof TendermintLockStakeTx txLockStake) {
                posState.addWeight(txLockStake.getSender(), txLockStake.getAmount());
            }

            if (tx instanceof TendermintUnlockStakeTx txUnlockStake) {
                posState.removeWeight(txUnlockStake.getSender(), txUnlockStake.getAmount());
            }
        }
    }

    // Methods.

    private void initTimeout() {
        timeoutPropose = 25L;
        timeoutPrevote = 25L;
        timeoutPrecommit = 25L;
        deltaTimeout = 10L;
    }

    private void startRound(long r) {
        if (height() == maxHeight() + 1) {
            log.debug("{} STOPPED -> height()>= {} reached", getAgent().getIdentifier(), maxHeight() + 1);
            if (!PalmBeachSimulation.isEnded())
                PalmBeachSimulation.scheduler().kill();
        }

        if (getAgent().isStarted()) {
            log.info("{} start new round {} -> Stage({}, {})", getAgent().getIdentifier(), round, height(), round);

            round = r;
            step = Step.PROPOSE;
            Block<TendermintTransaction> proposal;
            if (isProposer(getAgent().getIdentifier(), height(), round)) {
                proposal = Objects.requireNonNullElseGet(validValue, this::nextValue);
                log.info("{} proposer for Stage({}, {}), proposal hash -> {}", getAgent().getIdentifier(), height(), round,
                         proposal.sha256Base64Hash());
                broadcastProposal(height(), round, proposal, validRound);
            } else {
                scheduleTimeoutPropose(height(), round);
            }
        } else
            throw new AgentNotStartedException(getAgent().getIdentifier() + " is not started -> cannot start round " + r);
    }

    private long height() {
        return decision.currentHeight() + 1;
    }

    private boolean isProposer(SimpleAgent.AgentIdentifier agent, long h, long r) {
        return proposer(h, r).equals(agent);
    }

    private SimpleAgent.AgentIdentifier proposer(long h, long r) {
        List<SimpleAgent.AgentIdentifier> members = Lists.newArrayList(groupMembership());
        int index = (int) ((((members.size() + h) % members.size()) + r) % members.size());
        return members.get(index);
    }

    private Block<TendermintTransaction> nextValue() {
        final long h = height();
        final long timestamp = PalmBeachSimulation.scheduler().getCurrentTime();
        final String previous = decision.getBlock(height() - 1).sha256Base64Hash();
        final Set<TendermintTransaction> tx = selectTx();

        return new Block<>(h, timestamp, previous, tx);
    }

    private Set<TendermintTransaction> selectTx() {
        final Set<TendermintTransaction> txSet = Sets.newHashSet();

        int count = 0;
        for (int i = 0; i < memoryPool.size() && count < maxBlockSize(); i++) {
            TendermintTransaction tx = pollTx();
            if (isValidTx(tx)) {
                polledTx.add(tx);
                txSet.add(tx);
                count++;
            }
        }

        return txSet;
    }

    private TendermintTransaction pollTx() {
        TendermintTransaction tx = memoryPool.poll();
        setMemoryPool.remove(tx);
        return tx;
    }

    private boolean isValidTx(TendermintTransaction tx) {
        return tx.isBasicValid();
    }

    private void scheduleTimeoutPropose(long h, long r) {
        scheduleEvent(getAgent(), new TimeoutProposeEvent(new Stage(h, r)), timeoutPropose);
    }

    @Override
    protected ProtocolManipulator defaultProtocolManipulator() {
        return new DefaultProtocolManipulator(this);
    }

    @Override
    public void agentStarted() {
        computeCurrentCommittee();
        startRound(0L);
    }

    private void computeCurrentCommittee() {
        long seed = height();
        RandomGenerator rGenerator = new Random(seed);
        CommitteeSelector cSelector = CommitteeSelectorFactory.basicCommitteeSelector();
        currentCommittee = cSelector.selectCommittee(posState, committeeSize(), rGenerator);
        updateGroupMembership();
    }

    private void updateGroupMembership() {
        groupMembership.clear();
        for (SimpleAgent agent : PalmBeachSimulation.allAgents()) {
            TendermintValidator validator;
            if ((validator = agent.getProtocol(TendermintValidator.class)) != null && currentCommittee.contains(validator.getAddress())) {
                groupMembership.add(agent.getIdentifier());
            }
        }
    }

    @Override
    public void agentStopped() {
        // Nothing.
    }

    @Override
    public void agentKilled() {
        // Nothing.
    }

    @Override
    public void processEvent(Event<?> event) {
        if (getAgent().isStarted()) {
            TimeoutEvent timeoutEvent = (TimeoutEvent) event;
            timeoutEvent.onTimeout();
        } else
            throw new AgentNotStartedException(getAgent().getIdentifier() + " is not started -> cannot processEvent " + event);
    }

    @Override
    public boolean canProcessEvent(Event<?> event) {
        return event instanceof TimeoutEvent;
    }

    @Override
    public void messageDelivery(@NonNull MessageReceiver messageReceiver, Object contentDelivered) {
        if (getAgent().isStarted()) {
            if (contentDelivered instanceof ProposalMessage proMsg) {
                treatsProposalMessage(proMsg);
            } else if (contentDelivered instanceof PrevoteMessage preMsg) {
                treatsPrevoteMessage(preMsg);
            } else if (contentDelivered instanceof PrecommitMessage preComMsg) {
                treatsPrecommitMessage(preComMsg);
            } else if (contentDelivered instanceof TendermintClientMessage<?> clientMessage) {
                treatClientTendermintMessage(clientMessage);
            } else {
                log.error("Agent {} in Tendermint receive a object which is not a TendermintMessage -> Object receive {}",
                          getAgent().getIdentifier(), contentDelivered);
            }
        } else
            throw new AgentNotStartedException(getAgent().getIdentifier() + " is not started -> cannot delivered content " + contentDelivered);
    }

    private void treatsProposalMessage(ProposalMessage proMsg) {
        if (isProposer(proMsg.getSender(), height(), round) && !proposalReceived.contains(proMsg.getProposal())) {
            proposalReceived.add(proMsg.getProposal());
            executeRules(proMsg);
        }
    }

    private void treatsPrevoteMessage(PrevoteMessage preMsg) {
        Set<SimpleAgent.AgentIdentifier> agentSendPrevote = prevoteReceived.computeIfAbsent(preMsg.getStage(), k -> Sets.newHashSet());
        if (agentSendPrevote.add(preMsg.getSender())) {
            Prevote prevote = preMsg.getPrevote();
            long counter = prevoteCounter.computeIfAbsent(prevote, k -> 0L);
            prevoteCounter.put(prevote, counter + 1);
            executeRules(preMsg);
        }
    }

    private void treatsPrecommitMessage(PrecommitMessage preComMsg) {
        Set<SimpleAgent.AgentIdentifier> agentSendPrecommit = precommitReceived.computeIfAbsent(preComMsg.getStage(), k -> Sets.newHashSet());
        if (agentSendPrecommit.add(preComMsg.getSender())) {
            Precommit precommit = preComMsg.getPrecommit();
            long counter = precommitCounter.computeIfAbsent(precommit, k -> 0L);
            precommitCounter.put(precommit, counter + 1);
            executeRules(preComMsg);
        }
    }

    private void treatClientTendermintMessage(TendermintClientMessage<?> clientMessage) {
        if (clientMessage instanceof TransactionMessage txMessage) {
            TendermintTransaction tx = txMessage.getContent();
            if (!setMemoryPool.contains(tx)) {
                memoryPool.offer(tx);
                setMemoryPool.add(tx);
                getBroadcaster().broadcastMessage(txMessage, groupMembership(), getNetwork());
            }
        }
    }

    private void executeRules(TendermintValidatorMessage<?> tMsg) {
        for (Rule rule : rules) {
            if (!rule.isOnlyOneTimeRule()) {
                rule.execute(tMsg);
            } else {
                boolean evaluation = rule.evaluateCondition(tMsg);
                if (evaluation) {
                    Set<Stage> stages = ruleAlreadyExecuted.computeIfAbsent(rule, r -> Sets.newHashSet());
                    if (stages.add(new Stage(height(), round))) {
                        rule.execute(tMsg);
                    }
                }
            }
        }
    }

    private void broadcastProposal(long h, long r, Block<TendermintTransaction> proposal, long vR) {
        getBroadcaster().broadcastMessage(new ProposalMessage(getAgent().getIdentifier(), h, r, proposal, vR), groupMembership(), getNetwork());
    }

    private void broadcastPrevote(long h, long r, String value) {
        getBroadcaster().broadcastMessage(new PrevoteMessage(getAgent().getIdentifier(), h, r, value), groupMembership(), getNetwork());
    }

    private void broadcastPrecommit(long h, long r, String value) {
        getBroadcaster().broadcastMessage(new PrecommitMessage(getAgent().getIdentifier(), h, r, value), groupMembership(), getNetwork());
    }

    @Override
    public boolean interestedBy(Object contentDelivered) {
        return contentDelivered instanceof TendermintValidatorMessage || contentDelivered instanceof TendermintClientMessage ||
                contentDelivered instanceof NodeDirectoryMessage;
    }

    private boolean isValid(Block<TendermintTransaction> block, long h) {
        return block != null && block.getHeight() == h;
    }

    // Getters and Setters.

    @SuppressWarnings("unused")
    @Override
    public void setBroadcaster(Broadcaster broadcaster) {
        super.setBroadcaster(broadcaster);
        getBroadcaster().addObserver(this);
    }

    public Set<SimpleAgent.AgentIdentifier> groupMembership() {
        return groupMembership;
    }

    public long f() {
        Set<SimpleAgent.AgentIdentifier> members = groupMembership();
        long f = members.size() / 3;
        if (3 * f == members.size())
            f -= 1;
        return f;
    }

    public long maxHeight() {
        return getContext().getLong(MAX_HEIGHT, DEFAULT_MAX_HEIGHT, new Validate.MinLongValidator(5L, "MaxHeight must be greater or equal to 5"));
    }

    @SuppressWarnings("unused")
    public void maxHeight(long maxHeight) {
        getContext().setLong(MAX_HEIGHT, maxHeight, new Validate.MinLongValidator(5L, "MaxHeight must be greater or equal to 5"));
    }

    public int maxBlockSize() {
        return getContext().getInt(MAX_BLOCK_SIZE, DEFAULT_MAX_BLOCK_SIZE, new Validate.MinIntValidator(1, "MaxBlockSize must be greater or equal " +
                "to 1"));
    }

    @SuppressWarnings("unused")
    public void maxBlockSize(int maxBlockSize) {
        getContext().setInt(MAX_BLOCK_SIZE, maxBlockSize, new Validate.MinIntValidator(1, "MaxBlockSize must be greater or equal to 1"));
    }

    public int committeeSize() {
        return getContext().getInt(COMMITTEE_SIZE, DEFAULT_COMMITTEE_SIZE, new Validate.MinIntValidator(4, "CommitteeSize can be less then 4"));
    }

    @SuppressWarnings("unused")
    public void committeeSize(int committeeSize) {
        getContext().setInt(COMMITTEE_SIZE, committeeSize, new Validate.MinIntValidator(4, "CommitteeSize can be less then 4"));
    }

    // Inner classes.

    public enum Step {
        PROPOSE, PREVOTE, PRECOMMIT
    }

    public record Stage(long h, long r) {
    }

    private abstract class Rule {

        // Methods.

        public abstract boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg);

        public final void execute(@NonNull TendermintValidatorMessage<?> tMsg) {
            if (evaluateCondition(tMsg)) {
                execution(tMsg);
            }
        }

        protected abstract void execution(@NonNull TendermintValidatorMessage<?> tMsg);

        public abstract boolean isOnlyOneTimeRule();

        public long numberPrevoteFor(long h, long r) {
            Set<Prevote> matchingPrevote = prevoteCounter.keySet()
                    .stream().filter(prevote -> prevote.h() == h && prevote.r() == r)
                    .collect(Collectors.toSet());
            long count = 0L;
            for (Prevote prevote : matchingPrevote) {
                long counter = prevoteCounter.get(prevote);
                count += counter;
            }

            return count;
        }

        public long numberPrecommitFor(long h, long r) {
            Set<Precommit> matchingPrecommit = precommitCounter.keySet().stream()
                    .filter(precommit -> precommit.h() == h && precommit.r() == r)
                    .collect(Collectors.toSet());

            long count = 0L;
            for (Precommit precommit : matchingPrecommit) {
                long counter = precommitCounter.get(precommit);
                count += counter;
            }

            return count;
        }

        public long numberMessageFor(long h, long r) {
            Set<Proposal> matchingProposal = proposalReceived.stream().filter(proposal -> proposal.h() == h && proposal.r() == r)
                    .collect(Collectors.toSet());
            long numberMatchingPrevote = numberPrevoteFor(h, r);
            long numberMatchingPrecommit = numberPrecommitFor(h, r);

            return matchingProposal.size() + numberMatchingPrevote + numberMatchingPrecommit;
        }

        public Proposal findProposal(long h, long r) {
            for (Proposal proposal : proposalReceived) {
                if (proposal.h() == h && proposal.r() == r) {
                    return proposal;
                }
            }

            return null;
        }

        public Proposal findProposalByValidRound(long h, long vR) {
            for (Proposal proposal : proposalReceived) {
                if (proposal.h() == h && proposal.vR() == vR) {
                    return proposal;
                }
            }

            return null;
        }
    }

    private class ProposalForRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            if (tMsg instanceof ProposalMessage proMsg) {
                final long h = proMsg.getHeight();
                final long r = proMsg.getRound();
                final long vR = proMsg.getValidRound();

                return step == Step.PROPOSE
                        && height() == h
                        && round == r
                        && vR == -1;
            } else {
                return false;
            }
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            if (tMsg instanceof ProposalMessage proMsg) {
                final Block<TendermintTransaction> v = proMsg.getValue();
                log.debug("{} Execute ProposalForRound -> v {}, h {}, lockRound {}, lockedValue == v {}, isValid(v, height) {}",
                          getAgent().getIdentifier(), v.sha256Base64Hash(), height(), lockRound, Objects.equals(lockedValue, v),
                          isValid(v, height()));
                if (isValid(v, height()) && (lockRound == -1 || Objects.equals(lockedValue, v))) {
                    log.debug("{} Prevote v {}", getAgent().getIdentifier(), v.sha256Base64Hash());
                    broadcastPrevote(height(), round, v.sha256Base64Hash());
                } else {
                    log.debug("{} Prevote NIL", getAgent().getIdentifier());
                    broadcastPrevote(height(), round, null);
                }
                step = Step.PREVOTE;
            }
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return false;
        }
    }

    private class PrevoteForRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            if (tMsg instanceof PrevoteMessage preMsg) {
                return height() == preMsg.getHeight()
                        && round == preMsg.getRound()
                        && step == Step.PREVOTE
                        && numberPrevoteFor(height(), round) >= (2 * f() + 1);
            } else
                return false;
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            scheduleTimeoutPrevote(height(), round);
        }

        private void scheduleTimeoutPrevote(long h, long r) {
            log.debug("{} schedule TimeoutPrevote, Stage({}, {})", getAgent().getIdentifier(), height(), round);
            scheduleEvent(getAgent(), new TimeoutPrevoteEvent(new Stage(h, r)), timeoutPrevote);
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return true;
        }
    }

    private class PrevoteNilForRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            if (tMsg instanceof PrevoteMessage preMsg) {
                final long h = preMsg.getHeight();
                final long r = preMsg.getRound();
                final String idV = preMsg.getValue();
                final Prevote prevote = new Prevote(height(), round, idV);

                return step == Step.PREVOTE
                        && height() == h
                        && round == r
                        && idV == null
                        && (prevoteCounter.containsKey(prevote) && prevoteCounter.get(prevote) >= (2 * f() + 1));
            } else
                return false;
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            broadcastPrecommit(height(), round, null);
            step = Step.PRECOMMIT;
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return false;
        }
    }

    private class PrecommitForRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            if (tMsg instanceof PrecommitMessage preCoMsg) {
                boolean evaluation = height() == preCoMsg.getHeight()
                        && round == preCoMsg.getRound()
                        && numberPrecommitFor(height(), round) >= (2 * f() + 1);
                log.debug("{} evaluate PrecommitForRound, tMsg.stage = {}, evaluation = {}, nbPrecommit = {}, 2 * f + 1= {}",
                          getAgent().getIdentifier(),
                          tMsg.getStage(), evaluation, numberPrecommitFor(height(), round), (2 * f() + 1));
                return evaluation;
            } else
                return false;
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            log.debug("{} scheduleTimeoutPrecommit Stag({}, {})", getAgent().getIdentifier(), height(), round);
            scheduleTimeoutPrecommit(height(), round);
        }

        private void scheduleTimeoutPrecommit(long h, long r) {
            scheduleEvent(getAgent(), new TimeoutPrecommitEvent(new Stage(h, r)), timeoutPrecommit);
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return true;
        }
    }

    private class ChangeRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            return height() == tMsg.getHeight()
                    && round < tMsg.getRound()
                    && numberMessageFor(height(), tMsg.getRound()) >= (f() + 1);
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            startRound(tMsg.getRound());
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return false;
        }
    }

    private class ProposalAndPrevoteForValidRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            Proposal proposal;
            Prevote prevote;

            if (tMsg instanceof ProposalMessage proMsg) {
                proposal = proMsg.getProposal();
                prevote = new Prevote(proposal.h(), proposal.vR(), proposal.proposal().sha256Base64Hash());
            } else if (tMsg instanceof PrevoteMessage preMsg) {
                prevote = preMsg.getPrevote();
                proposal = findProposalByValidRound(prevote.h(), prevote.r());
                if (proposal == null || !proposal.proposal().sha256Base64Hash().equals(prevote.idV())) {
                    return false;
                }
            } else {
                return false;
            }

            final long vR = proposal.vR();

            return step == Step.PROPOSE
                    && height() == proposal.h()
                    && round == proposal.r()
                    && (vR >= 0 && vR < round)
                    && (prevoteCounter.containsKey(prevote) && prevoteCounter.get(prevote) >= (2 * f() + 1));
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            Proposal proposal = findProposal(height(), round);
            final Block<TendermintTransaction> v = proposal.proposal();
            final long vR = proposal.vR();

            if (isValid(v, height()) && (lockRound <= vR || Objects.equals(lockedValue, v))) {
                broadcastPrevote(height(), round, v.sha256Base64Hash());
            } else {
                broadcastPrevote(height(), round, null);
            }
            step = Step.PREVOTE;
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return false;
        }
    }

    private class ProposalAndPrevoteForRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            Proposal proposal;
            Prevote prevote;

            if (tMsg instanceof ProposalMessage proMsg) {
                proposal = proMsg.getProposal();
                prevote = new Prevote(proposal.h(), proposal.r(), proposal.proposal().sha256Base64Hash());
            } else if (tMsg instanceof PrevoteMessage preMsg) {
                prevote = preMsg.getPrevote();
                proposal = findProposal(prevote.h(), prevote.r());
                log.debug("{} ProposalAndPrevoteForRound by PrevoteMessage, prevote {}, proposal {}", getAgent().getIdentifier(), prevote,
                          proposal != null ? proposal.proposal().sha256Base64Hash() : null);
                if (proposal == null || !proposal.proposal().sha256Base64Hash().equals(prevote.idV())) {
                    log.debug("{} OUT Prevote.idV {}", getAgent().getIdentifier(), prevote.idV());
                    return false;
                }
            } else {
                return false;
            }

            final Block<TendermintTransaction> v = proposal.proposal();

            log.debug("{} evaluate ProposalAndPrevoteForRound, proposal = {}, prevote = {}, prevoteCounter.containsKey {}",
                      getAgent().getIdentifier(),
                      proposal.proposal().sha256Base64Hash(), prevote, prevoteCounter.containsKey(prevote));

            return (step == Step.PREVOTE || step == Step.PRECOMMIT)
                    && (height() == proposal.h() && round == proposal.r())
                    && isValid(v, height())
                    && (prevoteCounter.containsKey(prevote) && prevoteCounter.get(prevote) >= (2 * f() + 1));
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            final Block<TendermintTransaction> v = findProposal(height(), round).proposal();

            log.debug("{} receive 2f + 1 Prevote of v = {}", getAgent().getIdentifier(), v.sha256Base64Hash());

            if (step == Step.PREVOTE) {
                log.debug("{} broadcast Precommit for v = {}", getAgent().getIdentifier(), v.sha256Base64Hash());
                lockedValue = v;
                lockRound = round;
                broadcastPrecommit(height(), round, v.sha256Base64Hash());
                step = Step.PRECOMMIT;
            }
            validValue = v;
            validRound = round;
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return true;
        }
    }

    private class ProposalAndPrecommitForRound extends Rule {

        // Methods.

        @Override
        public boolean evaluateCondition(@NonNull TendermintValidatorMessage<?> tMsg) {
            Proposal proposal;
            Precommit precommit;

            if (tMsg instanceof ProposalMessage proMsg) {
                proposal = proMsg.getProposal();
                precommit = new Precommit(proposal.h(), proposal.r(), proposal.proposal().sha256Base64Hash());
            } else if (tMsg instanceof PrecommitMessage preCoMsg) {
                precommit = preCoMsg.getPrecommit();
                proposal = findProposal(precommit.h(), precommit.r());
                if (proposal == null || !proposal.proposal().sha256Base64Hash().equals(precommit.idV())) {
                    return false;
                }
            } else {
                return false;
            }

            return height() == proposal.h()
                    && (precommitCounter.containsKey(precommit) && precommitCounter.get(precommit) >= (2 * f() + 1))
                    && !decision.hasBlock(height());
        }

        @Override
        protected void execution(@NonNull TendermintValidatorMessage<?> tMsg) {
            final Block<TendermintTransaction> v = findProposal(height(), tMsg.getRound()).proposal();

            if (isValid(v, height())) {
                addBlock(v);
                nextHeight();
            }
        }

        private void addBlock(Block<TendermintTransaction> v) {
            // TODO Broadcast to other validators which are not in the committee.
            decision.addBlock(v);
            updatePoSState(v);
            Set<TendermintTransaction> blockTransactions = v.getTransactions();
            reAddPolledTx(blockTransactions);
            clearTxAddedInBlockchain(blockTransactions);
        }

        private void reAddPolledTx(Set<TendermintTransaction> blockTransactions) {
            for (TendermintTransaction tx : polledTx) {
                if (!blockTransactions.contains(tx)) {
                    memoryPool.add(tx);
                    setMemoryPool.add(tx);
                }
            }
        }

        private void clearTxAddedInBlockchain(Set<TendermintTransaction> blockTransactions) {
            for (TendermintTransaction tx : blockTransactions) {
                setMemoryPool.remove(tx);
                memoryPool.remove(tx);
            }
        }

        private void nextHeight() {
            mapHeightRound.put(height(), round);

            clearUselessProposal();
            clearUselessPrevote();
            clearUselessPrecommit();

            polledTx.clear();
            resetTendermint();
            computeCurrentCommittee();
            startRound(0);
        }

        private void clearUselessProposal() {
            proposalReceived.removeIf(proposal -> proposal.h <= height());
        }

        private void clearUselessPrevote() {
            prevoteReceived.entrySet().removeIf(entry -> {
                Stage stage = entry.getKey();
                return stage.h() <= height();
            });
            prevoteCounter.entrySet().removeIf(entry -> {
                Prevote prevote = entry.getKey();
                return prevote.h() <= height();
            });
        }

        private void clearUselessPrecommit() {
            precommitReceived.entrySet().removeIf(entry -> {
                Stage stage = entry.getKey();
                return stage.h() <= height();
            });
            precommitCounter.entrySet().removeIf(entry -> {
                Precommit prevote = entry.getKey();
                return prevote.h() <= height();
            });
        }

        private void resetTendermint() {
            lockRound = -1;
            lockedValue = null;
            validRound = -1;
            validValue = null;
            initTimeout();
        }

        @Override
        public boolean isOnlyOneTimeRule() {
            return false;
        }
    }

    public static record Proposal(long h, long r, Block<TendermintTransaction> proposal, long vR) {
    }

    public static record Prevote(long h, long r, String idV) {
    }

    public static record Precommit(long h, long r, String idV) {
    }

    @EqualsAndHashCode(callSuper = true)
    private abstract static class TimeoutEvent extends Event<Void> {

        @Getter
        @NonNull
        private final Stage stage;

        protected TimeoutEvent(@NonNull Stage stage) {
            super(null);
            this.stage = stage;
        }

        public abstract void onTimeout();
    }

    private class TimeoutProposeEvent extends TimeoutEvent {

        public TimeoutProposeEvent(@NonNull Stage stage) {
            super(stage);
        }

        @Override
        public void onTimeout() {
            if (height() == getStage().h() && round == getStage().r() && step == Step.PROPOSE) {
                log.debug("{} OnTimeoutPropose, Stage({}, {})", getAgent().getIdentifier(), height(), round);
                increaseTimeoutPropose(round);
                broadcastPrevote(height(), round, null);
                step = Step.PREVOTE;
            }
        }

        private void increaseTimeoutPropose(long r) {
            timeoutPropose = timeoutPropose + (r * deltaTimeout);
        }
    }

    private class TimeoutPrevoteEvent extends TimeoutEvent {

        public TimeoutPrevoteEvent(@NonNull Stage stage) {
            super(stage);
        }

        @Override
        public void onTimeout() {
            log.debug("In OnTimeoutPrevote");
            if (height() == getStage().h() && round == getStage().r() && step == Step.PREVOTE) {
                log.debug("{} OnTimeoutPrevote, Stage({}, {})", getAgent().getIdentifier(), height(), round);
                increaseTimeoutPrevote(round);
                broadcastPrecommit(height(), round, null);
                step = Step.PRECOMMIT;
            }
        }

        private void increaseTimeoutPrevote(long r) {
            timeoutPrevote = timeoutPrevote + (r * deltaTimeout);
        }
    }

    private class TimeoutPrecommitEvent extends TimeoutEvent {

        protected TimeoutPrecommitEvent(@NonNull Stage stage) {
            super(stage);
        }

        @Override
        public void onTimeout() {
            if (height() == getStage().h() && round == getStage().r()) {
                log.debug("{} OnTimeoutPrecommit, Stage({}, {}) -> start new round {}", getAgent().getIdentifier(), height(), round, round + 1);
                increaseTimeoutPrecommit(round);
                startRound(round + 1);
            }
        }

        private void increaseTimeoutPrecommit(long r) {
            timeoutPrecommit = timeoutPrecommit + (r * deltaTimeout);
        }
    }
}
