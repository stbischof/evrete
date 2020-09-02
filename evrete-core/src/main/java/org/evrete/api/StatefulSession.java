package org.evrete.api;

import java.util.Collection;
import java.util.List;

public interface StatefulSession extends WorkingMemory, RuntimeContext<StatefulSession> {

    void fire();

    void close();

    ActivationManager getActivationManager();

    void setActivationManager(ActivationManager activationManager);

    RuntimeRule getRule(String name);

    List<RuntimeRule> getRules();

    default RuntimeRule getRule(Named named) {
        return getRule(named.getName());
    }

    default void insertAndFire(Collection<?> objects) {
        insert(objects);
        fire();
    }

    default void insertAndFire(Object... objects) {
        insert(objects);
        fire();
    }

    default void deleteAndFire(Object... objects) {
        delete(objects);
        fire();
    }

    default void deleteAndFire(Collection<?> objects) {
        delete(objects);
        fire();
    }

}
