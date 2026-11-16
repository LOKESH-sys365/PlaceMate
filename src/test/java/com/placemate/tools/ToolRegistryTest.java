package com.placemate.tools;

import com.placemate.domain.DsaProblemRepository;
import com.placemate.domain.JobApplicationRepository;
import com.placemate.domain.ReminderRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ToolRegistryTest {

    private final ToolRegistry registry = new ToolRegistry(new PlacementTools(
            mock(JobApplicationRepository.class), mock(DsaProblemRepository.class), mock(ReminderRepository.class)));

    @Test
    void everyAnnotatedMethodIsRegisteredAsATool() {
        assertThat(registry.all()).hasSize(8);
    }

    @Test
    void toolsThatChangeDataRequireConfirmation() {
        assertThat(registry.requiresConfirmation("addApplication")).isTrue();
        assertThat(registry.requiresConfirmation("updateApplicationStatus")).isTrue();
        assertThat(registry.requiresConfirmation("logDsaProblem")).isTrue();
        assertThat(registry.requiresConfirmation("addReminder")).isTrue();
    }

    @Test
    void readOnlyToolsRunWithoutConfirmation() {
        assertThat(registry.requiresConfirmation("listApplications")).isFalse();
        assertThat(registry.requiresConfirmation("getDsaStats")).isFalse();
        assertThat(registry.requiresConfirmation("findWeakestTopics")).isFalse();
        assertThat(registry.requiresConfirmation("listUpcomingReminders")).isFalse();
    }

    @Test
    void selectedToolsAreSmallerThanAllTools() {
        var one = registry.all().subList(0, 1);
        assertThat(registry.approxTokens(one)).isLessThan(registry.approxTokens(registry.all()));
    }
}
