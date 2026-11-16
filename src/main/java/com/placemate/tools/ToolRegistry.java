package com.placemate.tools;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.*;

/** Holds every tool callback and knows which ones change data. */
@Component
public class ToolRegistry {

    private final List<ToolCallback> all;
    private final Set<String> confirmationRequired;

    public ToolRegistry(PlacementTools tools) {
        this.all = List.of(ToolCallbacks.from(tools));
        Set<String> names = new HashSet<>();
        for (Method m : ClassUtils.getUserClass(tools).getDeclaredMethods()) {
            Tool t = m.getAnnotation(Tool.class);
            if (t != null && m.isAnnotationPresent(RequiresConfirmation.class)) {
                names.add(t.name().isBlank() ? m.getName() : t.name());
            }
        }
        this.confirmationRequired = Set.copyOf(names);
    }

    public List<ToolCallback> all() {
        return all;
    }

    public boolean requiresConfirmation(String toolName) {
        return confirmationRequired.contains(toolName);
    }

    /** Rough size of the tool definitions the model would receive (about 4 characters per token). */
    public int approxTokens(Collection<ToolCallback> callbacks) {
        int chars = 0;
        for (ToolCallback cb : callbacks) {
            var d = cb.getToolDefinition();
            chars += d.name().length() + d.description().length() + d.inputSchema().length();
        }
        return chars / 4;
    }
}
