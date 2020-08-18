package org.evrete.runtime;

import org.evrete.api.ActiveField;
import org.evrete.api.RuntimeFact;
import org.evrete.runtime.evaluation.AlphaEvaluator;

import java.util.Arrays;

public final class RuntimeObject implements RuntimeFact {
    private static final boolean[] EMPTY_ALPHA_TESTS = new boolean[0];
    private Object[] values;
    private final Object delegate;
    private boolean[] alphaTests;

    private RuntimeObject(Object o, Object[] values) {
        this(o, values, EMPTY_ALPHA_TESTS);
    }

    private RuntimeObject(Object o, Object[] values, boolean[] alphaTests) {
        this.values = values;
        this.delegate = o;
        this.alphaTests = alphaTests;
    }

    @Override
    public Object apply(ActiveField field) {
        return values[field.getValueIndex()];
    }


    @Override
    public boolean[] getAlphaTests() {
        return alphaTests;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getDelegate() {
        return (T) delegate;
    }

    @Override
    public Object[] getValues() {
        return values;
    }

    public static RuntimeObject factory(Object o, Object[] values, boolean[] alphaTests) {
        return new RuntimeObject(o, values, alphaTests);
    }

    public static RuntimeObject factory(Object o, Object[] values) {
        return new RuntimeObject(o, values);
    }

    public final void appendValue(ActiveField field, Object value) {
        assert values.length == field.getValueIndex();
        this.values = Arrays.copyOf(this.values, values.length + 1);
        this.values[field.getValueIndex()] = value;
    }

    public final void appendAlphaTest(AlphaEvaluator[] newEvaluators) {
        int currentSize = this.alphaTests.length;
        this.alphaTests = Arrays.copyOf(this.alphaTests, currentSize + newEvaluators.length);
        for (int i = 0; i < newEvaluators.length; i++) {
            int newIndex = currentSize + i;
            AlphaEvaluator newEvaluator = newEvaluators[i];
            assert newIndex == newEvaluator.getUniqueId();
            Object fieldValue = values[newEvaluator.getValueIndex()];
            this.alphaTests[newIndex] = newEvaluator.test(fieldValue);
        }
    }

    public String toString() {
        return "{delegate=" + getDelegate() +
                ", values=" + Arrays.toString(values) +
                ", tests=" + Arrays.toString(alphaTests) +
                '}';
    }
}
