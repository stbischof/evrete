package org.evrete.runtime;

import org.evrete.api.RuntimeRule;
import org.evrete.runtime.memory.SessionMemory;

import java.util.*;

public class RuntimeRules implements Iterable<RuntimeRuleImpl>, MemoryChangeListener {
    private final List<RuntimeRuleImpl> list = new ArrayList<>();
    private final Collection<RuntimeAggregateLhsJoined> aggregateLhsGroups = new ArrayList<>();
    private final SessionMemory runtime;

    public RuntimeRules(SessionMemory runtime) {
        this.runtime = runtime;
    }

    private void add(RuntimeRuleImpl rule) {
        this.list.add(rule);
        this.aggregateLhsGroups.addAll(rule.getAggregateLhsGroups());
    }

    public RuntimeRuleImpl addRule(RuleDescriptor ruleDescriptor) {
        RuntimeRuleImpl r = new RuntimeRuleImpl(ruleDescriptor, runtime);
        this.add(r);
        return r;
    }

    public Collection<RuntimeAggregateLhsJoined> getAggregateLhsGroups() {
        return aggregateLhsGroups;
    }

    @Override
    public Iterator<RuntimeRuleImpl> iterator() {
        return list.iterator();
    }

    public List<RuntimeRule> asList() {
        return Collections.unmodifiableList(list);
    }

    @Override
    public void onAfterChange() {
        for (RuntimeRuleImpl rule : list) {
            rule.onAfterChange();
        }
    }
}
