package org.evrete.runtime;

import org.evrete.api.*;
import org.evrete.runtime.evaluation.BetaEvaluator;

import java.util.Arrays;
import java.util.function.Consumer;

public class BetaConditionNode extends AbstractBetaConditionNode {
    static final BetaConditionNode[] EMPTY_ARRAY = new BetaConditionNode[0];
    private final MemoryKeyMeta[] evaluationState;
    private final SourceMeta[] sourceMetas;
    private final IntToMemoryKey saveFunction;
    private final BetaEvaluator expression;

    BetaConditionNode(RuntimeRuleImpl rule, ConditionNodeDescriptor descriptor, BetaMemoryNode[] sources) {
        super(rule, descriptor, sources);
        this.expression = descriptor.getExpression().copyOf();
        ValueResolver valueResolver = rule.getRuntime().memory.memoryFactory.getValueResolver();
        FactType[] allFactTypes = rule.getFactTypes();
        this.evaluationState = new MemoryKeyMeta[allFactTypes.length];

        for (FactType type : allFactTypes) {
            MemoryKeyMeta keyMeta;
            if (expression.getFactTypeMask().get(type.getInRuleIndex())) {
                // This fact type is a part of condition evaluation
                keyMeta = new MemoryKeyMetaWithValues(type, valueResolver);
            } else {
                // This is a pass-through type, no field value reads are required
                keyMeta = new MemoryKeyMeta();
            }
            this.evaluationState[type.getInRuleIndex()] = keyMeta;
        }

        FactType[] myTypes = descriptor.getTypes();
        this.saveFunction = i -> evaluationState[myTypes[i].getInRuleIndex()].currentKey;
        this.sourceMetas = new SourceMeta[sources.length];
        for (int i = 0; i < sources.length; i++) {
            sourceMetas[i] = new SourceMeta(sources[i]);
        }

        BetaEvaluationValues conditionValues = ref -> evaluationState[ref.getFactType().getInRuleIndex()].value(ref.getFieldIndex());
        this.expression.setEvaluationState(conditionValues);
    }

    @Override
    public void commitDelta() {
        throw new UnsupportedOperationException();
    }

    private static void forEachConditionNode(BetaConditionNode node, Consumer<BetaConditionNode> consumer) {
        consumer.accept(node);
        for (BetaMemoryNode parent : node.getSources()) {
            if (parent.getDescriptor().isConditionNode()) {
                forEachConditionNode((BetaConditionNode) parent, consumer);
            }
        }
    }


/*
    private void debug() {
        System.out.println("Node:\t" + this + "\tsources:\t" + Arrays.toString(getSources()));
        System.out.println("\t\t\t\t\t\t\t\ttypes:\t\t" + Arrays.toString(getDescriptor().getTypes()));
        for (KeyMode keyMode : KeyMode.values()) {
            System.out.println("\t" + keyMode);
            ReIterator<MemoryKey[]> it = iterator(keyMode);
            it.reset();
            int counter = 0;
            while (it.hasNext()) {
                MemoryKey[] rows = it.next();
                System.out.println("\t\t" + counter + "\t" + Arrays.toString(rows));
                counter++;
            }
        }
        System.out.println("\n");
    }
*/

    public void computeDelta(boolean deltaOnly) {
        forEachKeyMode(0, false, false, new KeyMode[this.sourceMetas.length], deltaOnly);
        //debug();
    }

    private void forEachKeyMode(int sourceIndex, boolean hasDelta, boolean hasKnownKeys, KeyMode[] modes, boolean deltaOnly) {
        for (KeyMode mode : KeyMode.values()) {
            boolean newHasDelta = hasDelta || mode.isDeltaMode();
            boolean newHasKnownKeys = hasKnownKeys || (mode == KeyMode.KNOWN_UNKNOWN);
            modes[sourceIndex] = mode;
            if (sourceIndex == sourceMetas.length - 1) {
                if (newHasDelta || (!deltaOnly)) {
                    KeyMode destinationMode = newHasKnownKeys ? KeyMode.KNOWN_UNKNOWN : KeyMode.UNKNOWN_UNKNOWN;
                    forEachModeSelection(destinationMode, modes);
                }
            } else {
                forEachKeyMode(sourceIndex + 1, newHasDelta, newHasKnownKeys, modes, deltaOnly);
            }
        }
    }

    private void forEachModeSelection(KeyMode destinationMode, KeyMode[] sourceModes) {
        MemoryKeyCollection destination = getStore(destinationMode);
        for (int i = 0; i < sourceMetas.length; i++) {
            if (!sourceMetas[i].setIterator(sourceModes[i])) {
                return;
            }
        }
        // Reset cached states
        for (MemoryKeyMeta meta : evaluationState) {
            meta.clear();
        }
        // Evaluate current mode selection
        forEachMemoryKey(0, destination);
    }

    private void forEachMemoryKey(int sourceIndex, MemoryKeyCollection destination) {
        SourceMeta meta = this.sourceMetas[sourceIndex];
        ReIterator<MemoryKey> it = meta.currentIterator;
        if (it.reset() == 0) return;
        FactType[] types = meta.factTypes;
        boolean last = sourceIndex == this.sourceMetas.length - 1;

        while (it.hasNext()) {
            setState(it, types);
            if (last) {
                if (expression.test()) {
                    for (FactType type : getDescriptor().getTypes()) {
                        destination.add(evaluationState[type.getInRuleIndex()].currentKey);
                    }
                    //destination.save(saveFunction);
                }
            } else {
                forEachMemoryKey(sourceIndex + 1, destination);
            }
        }
    }

    private void setState(ReIterator<MemoryKey> it, FactType[] types) {
        for (FactType type : types) {
            //MemoryKey row = it.next();
            this.evaluationState[type.getInRuleIndex()].setKey(it.next());
        }
    }

    BetaEvaluator getExpression() {
        return expression;
    }

    void forEachConditionNode(Consumer<BetaConditionNode> consumer) {
        forEachConditionNode(this, consumer);
    }

    @Override
    public String toString() {
        return "{" +
                "node=" + expression +
                '}';
    }

    private static class SourceMeta {
        final BetaMemoryNode source;
        final FactType[] factTypes;
        ReIterator<MemoryKey> currentIterator;
        KeyMode currentMode;

        SourceMeta(BetaMemoryNode source) {
            this.source = source;
            this.factTypes = source.getDescriptor().getTypes();
        }

        boolean setIterator(KeyMode mode) {
            this.currentMode = mode;
            this.currentIterator = source.iterator(mode);
            return this.currentIterator.reset() > 0;
        }
    }

    private static class MemoryKeyMeta {
        MemoryKey currentKey;

        MemoryKeyMeta() {
        }

        void clear() {
            this.currentKey = null;
        }

        public void setKey(MemoryKey key) {
            this.currentKey = key;
        }

        Object value(int fieldIndex) {
            throw new UnsupportedOperationException();
        }
    }

    private static class MemoryKeyMetaWithValues extends MemoryKeyMeta {
        private final ActiveField[] fields;
        private final ValueHandle[] cachedValueHandles;
        private final Object[] cachedFieldValues;
        private final ValueResolver valueResolver;

        MemoryKeyMetaWithValues(FactType type, ValueResolver valueResolver) {
            this.fields = type.getFields().getFields();
            this.valueResolver = valueResolver;
            this.cachedValueHandles = new ValueHandle[this.fields.length];
            this.cachedFieldValues = new Object[this.fields.length];
        }

        void clear() {
            super.clear();
            Arrays.fill(this.cachedValueHandles, null);
            Arrays.fill(this.cachedFieldValues, null);
        }

        Object value(int fieldIndex) {
            return cachedFieldValues[fieldIndex];
        }

        public void setKey(MemoryKey key) {
            if (key != this.currentKey) {
                for (int i = 0; i < fields.length; i++) {
                    ValueHandle argHandle = key.get(i);
                    ValueHandle saved = cachedValueHandles[i];
                    if (argHandle != saved) {
                        // Reading field value
                        cachedFieldValues[i] = valueResolver.getValue(argHandle);
                        cachedValueHandles[i] = argHandle;
                    }
                }
                this.currentKey = key;
            }
        }
    }

}
