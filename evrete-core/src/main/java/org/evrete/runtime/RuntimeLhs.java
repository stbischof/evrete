package org.evrete.runtime;

import org.evrete.api.Action;
import org.evrete.api.RhsContext;
import org.evrete.api.RuntimeFact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class RuntimeLhs extends AbstractRuntimeLhs implements RhsContext {
    private final Collection<BetaEndNode> allBetaEndNodes = new ArrayList<>();
    private final Function<String, int[]> name2indices;
    private final RuntimeRuleImpl rule;
    private final StatefulSessionImpl workingMemory;

    RuntimeLhs(RuntimeRuleImpl rule, LhsDescriptor descriptor) {
        super(rule, descriptor);
        this.name2indices = descriptor.getNameIndices();
        this.allBetaEndNodes.addAll(getEndNodes());
        this.rule = rule;
        this.workingMemory = rule.getRuntime();
    }

    static RuntimeLhs factory(RuntimeRuleImpl rule, LhsDescriptor descriptor) {
        return new RuntimeLhsDefault(rule, descriptor);
    }

    @Override
    public RuntimeRuleImpl getRule() {
        return rule;
    }

    abstract void forEach(Consumer<RhsContext> rhs);

    public final Collection<BetaEndNode> getAllBetaEndNodes() {
        return allBetaEndNodes;
    }

    @Override
    public final RuntimeFact getFact(String name) {
        int[] arr = name2indices.apply(name);
        if (arr == null) throw new IllegalArgumentException("Unknown type reference: " + name);
        return factState[arr[0]][arr[1]];
    }

    @Override
    //TODO check if field values have _really_ changed
    public final RhsContext update(Object obj) {
        workingMemory.memoryAction(Action.UPDATE, obj);
        return this;
    }

    @Override
    public final RhsContext delete(Object obj) {
        workingMemory.memoryAction(Action.RETRACT, obj);
        return this;
    }

    @Override
    public final RhsContext insert(Object obj) {
        workingMemory.memoryAction(Action.INSERT, obj);
        return this;
    }
}
