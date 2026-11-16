package com.placemate.rag;

import com.placemate.domain.DsaProblemRepository;
import com.placemate.domain.JobApplicationRepository;
import com.placemate.domain.ReminderRepository;
import com.placemate.tools.PlacementTools;
import com.placemate.tools.ToolRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolSelectorTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final ToolRegistry registry = new ToolRegistry(new PlacementTools(
            mock(JobApplicationRepository.class), mock(DsaProblemRepository.class), mock(ReminderRepository.class)));
    private final ToolSelector selector = new ToolSelector(vectorStore, registry, 3);

    private static Document hit(String toolName) {
        return new Document(toolName + "-id", toolName, Map.of("toolName", toolName));
    }

    private static List<String> names(List<ToolCallback> callbacks) {
        return callbacks.stream().map(c -> c.getToolDefinition().name()).toList();
    }

    @Test
    void returnsOnlyTheToolsTheVectorSearchFound() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit("logDsaProblem"), hit("getDsaStats")));

        assertThat(names(selector.select("I solved two sum"))).containsExactlyInAnyOrder("logDsaProblem", "getDsaStats");
    }

    @Test
    void statusUpdatesAlsoGetTheListingToolSoTheModelCanFindTheId() {
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(hit("updateApplicationStatus")));

        assertThat(names(selector.select("move Razorpay to interview")))
                .containsExactlyInAnyOrder("updateApplicationStatus", "listApplications");
    }

    @Test
    void fallsBackToAllToolsWhenNothingMatches() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        assertThat(selector.select("hello")).hasSameSizeAs(registry.all());
    }
}
