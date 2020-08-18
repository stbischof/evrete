package org.evrete.runtime.memory;

import org.evrete.api.KeysStore;
import org.evrete.api.ReIterator;
import org.evrete.api.ValueRow;
import org.evrete.collections.MappedReIterator;
import org.evrete.runtime.ConditionNodeDescriptor;
import org.evrete.runtime.FactType;
import org.evrete.runtime.RuntimeFactType;
import org.evrete.runtime.RuntimeRuleImpl;
import org.evrete.runtime.evaluation.EvaluatorGroup;

import java.util.ArrayList;
import java.util.List;

public class AbstractBetaConditionNode implements BetaMemoryNode<ConditionNodeDescriptor> {
    private final int[] nonPlainSourceIndices;
    private final KeysStore mainStore;
    private final KeysStore deltaStore;
    private final EvaluatorGroup expression;
    private final ConditionNodeDescriptor descriptor;
    private final BetaMemoryNode<?>[] sources;
    private final BetaConditionNode[] conditionSources;
    private final RuntimeFactType[][] grouping;
    private final RuntimeRuleImpl rule;
    private final ReIterator<ValueRow[]> mainIterator;
    private final ReIterator<ValueRow[]> deltaIterator;

    AbstractBetaConditionNode(RuntimeRuleImpl rule, ConditionNodeDescriptor descriptor, BetaMemoryNode<?>[] sources) {
        this.sources = sources;
        List<BetaConditionNode> conditionNodeList = new ArrayList<>(sources.length);
        for (BetaMemoryNode<?> source : sources) {
            if (source.isConditionNode()) {
                conditionNodeList.add((BetaConditionNode) source);
            }
        }
        this.conditionSources = conditionNodeList.toArray(BetaConditionNode.EMPTY_ARRAY);
        this.rule = rule;
        this.descriptor = descriptor;
        this.nonPlainSourceIndices = descriptor.getNonPlainSourceIndices();
        SessionMemory memory = rule.getMemory();
        this.mainStore = memory.newKeysStore(descriptor.getEvalGrouping());
        this.deltaStore = memory.newKeysStore(descriptor.getEvalGrouping());
        this.expression = descriptor.getExpression();

        FactType[][] descGrouping = descriptor.getEvalGrouping();
        this.grouping = new RuntimeFactType[descGrouping.length][];
        for (int i = 0; i < descGrouping.length; i++) {
            this.grouping[i] = rule.resolve(descGrouping[i]);
        }

        this.mainIterator = new MappedReIterator<>(mainStore.entries(), KeysStore.Entry::key);
        this.deltaIterator = new MappedReIterator<>(deltaStore.entries(), KeysStore.Entry::key);
    }

    public BetaConditionNode[] getConditionSources() {
        return conditionSources;
    }

    public boolean hasMainData() {
        return !mainStore.isEmpty();
    }

    @Override
    public KeysStore getDeltaStore() {
        return this.deltaStore;
    }

    @Override
    public SessionMemory getRuntime() {
        return rule.getMemory();
    }


    public RuntimeFactType[][] getGrouping() {
        return grouping;
    }

    public BetaMemoryNode<?>[] getSources() {
        return sources;
    }

    @Override
    public ConditionNodeDescriptor getDescriptor() {
        return descriptor;
    }

    public int[] getNonPlainSourceIndices() {
        return nonPlainSourceIndices;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "condition=" + expression +
                '}';
    }

    @Override
    public KeysStore getMainStore() {
        return mainStore;
    }

    public ReIterator<ValueRow[]> mainIterator() {
        return mainIterator;
    }

    public ReIterator<ValueRow[]> deltaIterator() {
        return deltaIterator;
    }
}
