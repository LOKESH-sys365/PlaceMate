package com.placemate.chat;

import com.placemate.domain.*;
import com.placemate.tools.PlacementTools;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;

/** Read-only data for the desk view. Writes only ever happen through approved tool calls. */
@RestController
@RequestMapping("/api")
public class DashboardController {

    public record ApplicationRow(Long id, String company, String role, String status, String appliedOn, String link) {}
    public record TopicRow(String topic, long solved) {}
    public record ReminderRow(Long id, String text, String dueDate) {}
    public record Dashboard(String today, List<ApplicationRow> applications, long totalSolved,
                            List<TopicRow> topics, List<ReminderRow> reminders) {}

    private final JobApplicationRepository applications;
    private final DsaProblemRepository problems;
    private final ReminderRepository reminders;

    public DashboardController(JobApplicationRepository applications,
                               DsaProblemRepository problems,
                               ReminderRepository reminders) {
        this.applications = applications;
        this.problems = problems;
        this.reminders = reminders;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        LocalDate today = LocalDate.now();

        List<ApplicationRow> apps = applications.findAll().stream()
                .sorted(Comparator.comparing(JobApplication::getId).reversed())
                .map(a -> new ApplicationRow(a.getId(), a.getCompany(), a.getRole(),
                        a.getStatus().name(), String.valueOf(a.getAppliedOn()), a.getLink()))
                .toList();

        Map<String, Long> counts = new HashMap<>();
        long total = 0;
        for (DsaProblemRepository.TopicCount t : problems.countByTopic()) {
            counts.put(t.getTopic(), t.getTotal());
            total += t.getTotal();
        }
        Set<String> topicNames = new LinkedHashSet<>(PlacementTools.CORE_TOPICS);
        topicNames.addAll(counts.keySet());
        List<TopicRow> topics = topicNames.stream()
                .map(n -> new TopicRow(n, counts.getOrDefault(n, 0L)))
                .sorted(Comparator.comparingLong(TopicRow::solved).thenComparing(TopicRow::topic))
                .toList();

        List<ReminderRow> due = reminders
                .findByDueDateBetweenOrderByDueDate(today.minusDays(14), today.plusDays(60)).stream()
                .map(r -> new ReminderRow(r.getId(), r.getText(), r.getDueDate().toString()))
                .toList();

        return new Dashboard(today.toString(), apps, total, topics, due);
    }
}
