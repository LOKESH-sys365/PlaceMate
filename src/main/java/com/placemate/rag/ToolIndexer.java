package com.placemate.rag;

import com.placemate.tools.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Embeds each tool's name and description into pgvector at startup. Ids are derived
 * from the tool name, so restarting updates the rows instead of duplicating them.
 */
@Component
public class ToolIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolIndexer.class);

    private final ToolRegistry registry;
    private final VectorStore vectorStore;

    public ToolIndexer(ToolRegistry registry, VectorStore vectorStore) {
        this.registry = registry;
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Document> docs = registry.all().stream().map(cb -> {
            var def = cb.getToolDefinition();
            String id = UUID.nameUUIDFromBytes(("tool:" + def.name()).getBytes(StandardCharsets.UTF_8)).toString();
            return new Document(id, def.name() + ": " + def.description(), Map.of("toolName", def.name()));
        }).toList();
        vectorStore.add(docs);
        log.info("Indexed {} tools into the vector store", docs.size());
    }
}
