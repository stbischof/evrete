package org.evrete.dsl;

import org.evrete.KnowledgeService;
import org.evrete.api.Knowledge;
import org.evrete.api.RuleScope;
import org.evrete.api.StatefulSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FilePermission;
import java.io.IOException;

class JavaSourceSecurityTests {
    private KnowledgeService service;

    @BeforeEach
    void init() {
        service = new KnowledgeService();
    }

    @Test
    void rhsTestFail1() {
        assert System.getSecurityManager() != null;

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    Knowledge knowledge = service.newKnowledge(AbstractDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest1.java"));
                    try(StatefulSession session = knowledge.newStatefulSession()){
                        session.insert(new Object());
                        session.fire();
                    }
                }
        );
    }

    @Test
    void rhsTestFail2() {
        assert System.getSecurityManager() != null;

        service
                .getSecurity()
                .addPermission(RuleScope.LHS, new FilePermission(".", "read"));

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    Knowledge knowledge = service.newKnowledge(DSLSourceProvider.class, new File("src/test/resources/java/SecurityTest1.java"));
                    try(StatefulSession session = knowledge.newStatefulSession()){
                        session.insert(new Object());
                        session.fire();
                    }
                }
        );
    }

    @Test
    void rhsTestFail3() {
        assert System.getSecurityManager() != null;

        service.getSecurity().addPermission(RuleScope.RHS, new FilePermission(".", "read"));

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    Knowledge knowledge = service.newKnowledge(AbstractDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest1.java"));
                    try(StatefulSession session = knowledge.newStatefulSession()){
                        session.insert(new Object());
                        session.fire();
                    }
                }
        );
    }

    @Test
    void rhsTestPass() throws IOException {
        assert System.getSecurityManager() != null;

        service.getSecurity().addPermission(RuleScope.BOTH, new FilePermission(".", "read"));
        Knowledge knowledge = service.newKnowledge(AbstractDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest1.java"));
        try(StatefulSession session = knowledge.newStatefulSession()) {
            session.insert(new Object());
            session.fire();
        }
    }

    @Test
    void lhsTestFail1() {
        assert System.getSecurityManager() != null;

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    service.getConfiguration().addImport(RuleScope.LHS, File.class);
                    Knowledge knowledge = service.newKnowledge(DSLSourceProvider.class, new File("src/test/resources/java/SecurityTest2.java"));
                    try(StatefulSession session = knowledge.newStatefulSession()){
                        session.insert(5);
                        session.fire();
                    }
                }
        );
    }

    @Test
    void lhsTestPass() throws IOException {
        assert System.getSecurityManager() != null;
        service.getConfiguration().addImport(RuleScope.LHS, File.class);
        service.getSecurity().addPermission(RuleScope.BOTH, new FilePermission("5", "read"));
        Knowledge knowledge = service.newKnowledge(DSLSourceProvider.class, new File("src/test/resources/java/SecurityTest2.java"));
        try (StatefulSession session = knowledge.newStatefulSession()) {
            session.insert(5);
            session.fire();
        }
    }


    @Test
    void indirectAccessFail() {
        assert System.getSecurityManager() != null;

        Assertions.assertThrows(
                SecurityException.class,
                () -> {
                    Knowledge knowledge = service.newKnowledge(AbstractDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest3.java"));
                    try(StatefulSession session = knowledge.newStatefulSession()){
                        session.insert(5);
                        session.fire();
                    }
                }
        );
    }

    @Test
    void indirectAccessPass() throws IOException {
        assert System.getSecurityManager() != null;
        service.getSecurity().addPermission(RuleScope.BOTH, new FilePermission("<<ALL FILES>>", "read"));
        Knowledge knowledge = service.newKnowledge(AbstractDSLProvider.PROVIDER_JAVA_S, new File("src/test/resources/java/SecurityTest3.java"));
        try(StatefulSession session = knowledge.newStatefulSession()){
            session.insert(5);
            session.fire();
        }
    }
}
