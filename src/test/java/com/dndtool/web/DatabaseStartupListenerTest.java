package com.dndtool.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dndtool.persistence.DatabaseSchemaStatus;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.annotation.WebListener;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class DatabaseStartupListenerTest {
    @Test
    void startupPublishesTheSameAttributeAndLogsOnlyTheFiniteCategory() {
        assertNotNull(DatabaseStartupListener.class.getAnnotation(WebListener.class));
        for (DatabaseSchemaStatus.State state : DatabaseSchemaStatus.State.values()) {
            Map<String, Object> attributes = new HashMap<>();
            List<String> log = new ArrayList<>();
            AtomicInteger calls = new AtomicInteger();
            DatabaseSchemaStatus status = new DatabaseSchemaStatus(state, 0, "private script", "private hash");
            ServletContext context = (ServletContext) Proxy.newProxyInstance(
                    ServletContext.class.getClassLoader(), new Class<?>[] {ServletContext.class},
                    (ignored, method, arguments) -> switch (method.getName()) {
                        case "setAttribute" -> { attributes.put((String) arguments[0], arguments[1]); yield null; }
                        case "log" -> { log.add((String) arguments[0]); yield null; }
                        default -> throw new AssertionError("Unexpected context call: " + method.getName());
                    });
            DatabaseStartupListener listener = new DatabaseStartupListener(() -> {
                calls.incrementAndGet();
                return status;
            });
            assertEquals(0, calls.get());

            listener.contextInitialized(new ServletContextEvent(context));

            assertEquals(1, calls.get());
            assertEquals(Map.of("com.dndtool.persistence.databaseSchemaStatus", status), attributes);
            assertEquals(List.of("Database readiness check finished with state " + state + "."), log);
        }
    }
}
