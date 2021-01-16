package org.evrete.runtime;

import org.evrete.api.KeyReIterators;
import org.evrete.api.Memory;
import org.evrete.api.SharedBetaFactStorage;
import org.evrete.api.ValueRow;
import org.evrete.util.ValueRowToArray;

public class RuntimeFactTypeKeyed extends RuntimeFactType {
    private final SharedBetaFactStorage keyStorage;
    private final KeyReIterators<ValueRow> keyIterators;
    private final KeyReIterators<ValueRow[]> mappedKeyIterators;

    public RuntimeFactTypeKeyed(SessionMemory runtime, FactType other) {
        super(runtime, other);
        this.keyStorage = runtime.getBetaFactStorage(other);
        this.keyIterators = runtime.getBetaFactStorage(other).keyIterators();
        this.mappedKeyIterators = runtime.getBetaFactStorage(other).keyIterators(ValueRowToArray.SUPPLIER);
    }

    public RuntimeFactTypeKeyed(RuntimeFactTypeKeyed other) {
        super(other.getRuntime(), other);
        this.keyStorage = other.keyStorage;
        this.keyIterators = other.keyIterators;
        this.mappedKeyIterators = other.mappedKeyIterators;
    }


    @Override
    public Memory getSource() {
        return keyStorage;
    }

    public KeyReIterators<ValueRow> getKeyIterators() {
        return keyIterators;
    }

    public KeyReIterators<ValueRow[]> getMappedKeyIterators() {
        return mappedKeyIterators;
    }
}
