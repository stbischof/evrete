package org.evrete.runtime.memory;

import org.evrete.api.*;
import org.evrete.api.spi.SharedBetaFactStorage;
import org.evrete.collections.FastHashMap;
import org.evrete.runtime.*;
import org.evrete.runtime.async.*;
import org.evrete.runtime.structure.FactType;
import org.evrete.runtime.structure.RuleDescriptor;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

public class SessionMemory extends AbstractRuntime<StatefulSession> implements WorkingMemory, MemoryChangeListener {
    private final BufferSafe buffer;
    private final RuntimeRules ruleStorage;
    private final FastHashMap<Type, TypeMemory> typedMemories;

    protected SessionMemory(KnowledgeImpl parent) {
        super(parent);
        this.buffer = new BufferSafe();
        this.ruleStorage = new RuntimeRules(this);
        this.typedMemories = new FastHashMap<>(getTypeResolver().getKnownTypes().size());
        // Deploy existing rules
        for (RuleDescriptor descriptor : getRuleDescriptors()) {
            deployRule(descriptor, false);
        }
    }

    @Override
    public final Kind getKind() {
        return Kind.SESSION;
    }

    @Override
    public RuntimeRule deployRule(RuleDescriptor descriptor) {
        return deployRule(descriptor, true);
    }

    private synchronized RuntimeRule deployRule(RuleDescriptor descriptor, boolean hotDeployment) {
        for (FactType factType : descriptor.getRootLhsDescriptor().getAllFactTypes()) {
            touchMemory(factType.getFields(), factType.getAlphaMask());
        }
        RuntimeRule rule = ruleStorage.addRule(descriptor);
        if (hotDeployment) {
            getExecutor().invoke(new RuleHotDeploymentTask(rule));
        }
        return rule;
    }

    private void touchMemory(FieldsKey key, AlphaBucketMeta alphaMeta) {
        Type t = key.getType();
        typedMemories
                .computeIfAbsent(t, k -> new TypeMemory(this, t))
                .touchMemory(key, alphaMeta);
    }

    @Override
    public void clear() {
        buffer.clear();
        typedMemories.forEachValue(TypeMemory::clear);
    }

    @Override
    public final void insert(Collection<?> objects) {
        if (objects == null) return;
        this.buffer.add(getTypeResolver(), Action.INSERT, objects);
    }

    @Override
    protected synchronized void onNewActiveField(ActiveField newField) {
        Type t = newField.getDeclaringType();
        TypeMemory tm = typedMemories.get(t);
        if (tm == null) {
            tm = new TypeMemory(this, t);
            typedMemories.put(t, tm);
        } else {
            tm.onNewActiveField(newField);
        }
    }

    @Override
    protected void onNewAlphaBucket(AlphaDelta delta) {
        Type t = delta.getKey().getType();
        TypeMemory tm = typedMemories.get(t);
        if (tm == null) {
            tm = new TypeMemory(this, t);
            typedMemories.put(t, tm);
        } else {
            tm.onNewAlphaBucket(delta);
        }
    }

    public BufferSafe getBuffer() {
        return buffer;
    }

    public SharedBetaFactStorage getBetaFactStorage(FactType factType) {
        Type t = factType.getType();
        FieldsKey fields = factType.getFields();
        AlphaBucketMeta mask = factType.getAlphaMask();

        return get(t).get(fields).get(mask);
    }

    @Override
    public final void delete(Collection<?> objects) {
        if (objects == null) return;
        this.buffer.add(getTypeResolver(), Action.RETRACT, objects);
    }

    @Override
    public void update(Collection<?> objects) {
        if (objects == null) return;
        this.buffer.add(getTypeResolver(), Action.UPDATE, objects);
    }

    protected void destroy() {
        buffer.clear();
        typedMemories.clear();
    }

    @Override
    public <T> void forEachMemoryObject(String type, Consumer<T> consumer) {
        Type t = getTypeResolver().getType(type);
        TypeMemory tm = typedMemories.get(t);
        if (tm != null) {
            tm.forEachMemoryObject(consumer);
        }
    }

    @Override
    public void forEachMemoryObject(Consumer<Object> consumer) {
        typedMemories.forEachValue(mem -> mem.forEachObjectUnchecked(consumer));
    }

    @Override
    public List<RuntimeRule> getRules() {
        return ruleStorage.asList();
    }

    protected void handleBuffer() {
        onBeforeChange();
        List<Completer> tasksQueue = new LinkedList<>();

        // 1. Deletes
        handleDeletes(tasksQueue);

        // 2. Do inserts
        handleInserts(tasksQueue);

        // 3. Execute all tasks orderly
        ForkJoinExecutor executor = getExecutor();
        for (Completer task : tasksQueue) {
            executor.invoke(task);
        }

        onAfterChange();
    }

    @Override
    public void onAfterChange() {
        buffer.clear();
        ruleStorage.onAfterChange();
        typedMemories.forEachValue(MemoryChangeListener::onAfterChange);
    }

    @Override
    public void onBeforeChange() {
        ruleStorage.onBeforeChange();
        typedMemories.forEachValue(MemoryChangeListener::onBeforeChange);
    }

    private void handleDeletes(List<Completer> tasksQueue) {
        buffer.takeAll(
                Action.RETRACT,
                (type, iterator) -> {
                    TypeMemory tm = get(type);
                    while (iterator.hasNext()) {
                        tm.deleteSingle(iterator.next());
                    }
                    tm.commitDelete();
                }
        );

        Collection<RuntimeRule> ruleDeleteChanges = new LinkedList<>();
        for (RuntimeRule rule : ruleStorage) {
            if (rule.isDeleteDeltaAvailable()) {
                ruleDeleteChanges.add(rule);
            }
        }
        if (!ruleDeleteChanges.isEmpty()) {
            tasksQueue.add(new RuleMemoryDeleteTask(ruleDeleteChanges));
        }
    }

    private void handleInserts(List<Completer> tasksQueue) {
        buffer.takeAll(
                Action.INSERT,
                (type, iterator) -> {
                    TypeMemory tm = get(type);
                    while (iterator.hasNext()) {
                        tm.insertSingle(iterator.next());
                    }
                    tm.commitInsert();
                }
        );

        // Ordered task 1 - update end nodes
        Collection<RuntimeRule> ruleInsertChanges = new LinkedList<>();

        for (RuntimeRule rule : ruleStorage) {
            if (rule.isInsertDeltaAvailable()) {
                ruleInsertChanges.add(rule);
            }
        }

        if (!ruleInsertChanges.isEmpty()) {
            tasksQueue.add(new RuleMemoryInsertTask(ruleInsertChanges, true));
        }


        // Ordered task 2 - update aggregate nodes
        Collection<RuntimeAggregateLhsJoined> aggregateGroups = ruleStorage.getAggregateLhsGroups();
        if (!aggregateGroups.isEmpty()) {
            tasksQueue.add(new AggregateComputeTask(aggregateGroups, true));
        }

    }

/*
    public void init1(FactType factType) {
        Type t = factType.getType();
        TypeMemory tm = typedMemories.get(t);
        if (tm == null) {
            tm = new TypeMemory(this, t);
            typedMemories.put(t, tm);
        }
        AlphaBucketMeta mask = factType.getAlphaMask();
        FieldsKey key = factType.getFields();
        tm.init111(key, mask);
    }
*/

    public TypeMemory get(Type t) {
        TypeMemory m = typedMemories.get(t);
        if (m == null) {
            throw new IllegalArgumentException("No type memory created for " + t);
        } else {
            return m;
        }
    }

    protected boolean hasMemoryTasks() {
        return buffer.hasTasks();
    }
}
