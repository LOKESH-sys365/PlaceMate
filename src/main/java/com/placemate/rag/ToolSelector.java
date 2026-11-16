package com.placemate.rag;

import com.placemate.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Picks the few tools that are relevant to a message, so the model is sent a handful of
 * tool definitions instead of all of them.
 */
@Component
public class ToolSelector {

    private static final Logger log = LoggerFactory.getLogger(ToolSelector.class);

    /** Tools that are only useful together: asking for the key tool also brings in its helper. */
    private static final Map<String, String> COMPANION_TOOLS = Map.of(
            "updateApplicationStatus", "listApplications");

    private final VectorStore vectorStore;
    private final ToolRegistry registry;
    private final int topK;

    public ToolSelector(VectorStore vectorStore,
                        ToolRegistry registry,
                        @Value("${placemate.rag.top-k:3}") int topK) {
        this.vectorStore = vectorStore;
        this.registry = registry;
        this.topK = topK;
    }

    public List<ToolCallback> select(String message) {
        List<Document> hits = vectorStore.similaritySearch(
                SearchRequest.builder().query(message).topK(topK).build());

        Set<String> names = hits.stream()
                .map(d -> String.valueOf(d.getMetadata().get("toolName")))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String name : new ArrayList<>(names)) {
            String companion = COMPANION_TOOLS.get(name);
            if (companion != null) {
                names.add(companion);
            }
        }

        List<ToolCallback> selected = registry.all().stream()
                .filter(cb -> names.contains(cb.getToolDefinition().name()))
                .toList();

        if (selected.isEmpty()) {
            log.warn("Vector search returned no known tools; falling back to all tools");
            return registry.all();
        }
        log.debug("Selected tools {} for message", names);
        return selected;
    }
}
