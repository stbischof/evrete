package org.evrete.util;

import org.evrete.api.*;
import org.evrete.runtime.RuleDescriptor;

import java.util.Collection;
import java.util.List;

public abstract class KnowledgeWrapper extends RuntimeContextWrapper<Knowledge> implements Knowledge {

    protected KnowledgeWrapper(Knowledge delegate) {
        super(delegate);
    }

    @Override
    public Collection<RuleSession<?>> getSessions() {
        return delegate.getSessions();
    }

    @Override
    public StatefulSession newStatefulSession() {
        return delegate.newStatefulSession();
    }

    @Override
    public StatelessSession newStatelessSession() {
        return delegate.newStatelessSession();
    }

    @Override
    public List<RuleDescriptor> getRules() {
        return delegate.getRules();
    }

    @Override
    public RuleDescriptor compileRule(RuleBuilder<?> builder) {
        return delegate.compileRule(builder);
    }

    @Override
    public RuleDescriptor getRule(String name) {
        return delegate.getRule(name);
    }

    @Override
    public FieldReference[] resolveFieldReferences(String[] args, NamedType.Resolver typeMapper) {
        return delegate.resolveFieldReferences(args, typeMapper);
    }

}
