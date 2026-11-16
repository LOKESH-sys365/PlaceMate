package com.placemate.tools;

import com.placemate.domain.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Everything the assistant is allowed to do. Read tools run immediately;
 * tools marked {@link RequiresConfirmation} wait for the user's approval.
 */
@Component
public class PlacementTools {

    public static final List<String> CORE_TOPICS = List.of(
            "arrays", "strings", "hashing", "binary search", "recursion", "linked list",
            "stack", "queue", "trees", "graphs", "greedy", "dp");

    private static final Set<String> DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");

    public record ApplicationView(Long id, String company, String role, String status, String appliedOn, String link) {
        static ApplicationView of(JobApplication a) {
            return new ApplicationView(a.getId(), a.getCompany(), a.getRole(), a.getStatus().name(),
                    String.valueOf(a.getAppliedOn()), a.getLink());
        }
    }

    public record ProblemView(Long id, String title, String topic, String difficulty) {}

    public record TopicCountView(String topic, long solved) {}

    public record DsaStats(long totalSolved, Map<String, Long> solvedByTopic) {}

    public record ReminderView(Long id, String text, String dueDate) {}

    private final JobApplicationRepository applications;
    private final DsaProblemRepository problems;
    private final ReminderRepository reminders;

    public PlacementTools(JobApplicationRepository applications,
                          DsaProblemRepository problems,
                          ReminderRepository reminders) {
        this.applications = applications;
        this.problems = problems;
        this.reminders = reminders;
    }

    // ---------------------------------------------------------------- applications

    @Tool(description = "List the user's job and internship applications with their ids. "
            + "Optionally filter by status: APPLIED, ONLINE_ASSESSMENT, INTERVIEW, OFFER or REJECTED. "
            + "Call this first whenever you need an application id.")
    public List<ApplicationView> listApplications(
            @ToolParam(description = "Status filter; leave empty to list every application", required = false) String status) {
        List<JobApplication> found = (status == null || status.isBlank())
                ? applications.findAll()
                : applications.findByStatus(parseStatus(status));
        return found.stream().map(ApplicationView::of).toList();
    }

    @Tool(description = "Record a new job or internship application. New applications start in the APPLIED stage.")
    @RequiresConfirmation
    public ApplicationView addApplication(
            @ToolParam(description = "Company name") String company,
            @ToolParam(description = "Role title, e.g. SDE Intern") String role,
            @ToolParam(description = "Link to the job posting", required = false) String link) {
        if (company == null || company.isBlank() || role == null || role.isBlank()) {
            throw new IllegalArgumentException("Both company and role are required.");
        }
        JobApplication a = new JobApplication();
        a.setCompany(company.trim());
        a.setRole(role.trim());
        a.setLink(link == null || link.isBlank() ? null : link.trim());
        return ApplicationView.of(applications.save(a));
    }

    @Tool(description = "Move an existing application to a new stage: APPLIED, ONLINE_ASSESSMENT, INTERVIEW, OFFER or REJECTED.")
    @RequiresConfirmation
    public ApplicationView updateApplicationStatus(
            @ToolParam(description = "Application id, taken from listApplications") Long id,
            @ToolParam(description = "New status") String status) {
        JobApplication a = applications.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No application with id " + id));
        a.setStatus(parseStatus(status));
        return ApplicationView.of(applications.save(a));
    }

    // ---------------------------------------------------------------- DSA practice

    @Tool(description = "Log a DSA practice problem the user has solved, with its topic and difficulty.")
    @RequiresConfirmation
    public ProblemView logDsaProblem(
            @ToolParam(description = "Problem title, e.g. Course Schedule") String title,
            @ToolParam(description = "Topic such as arrays, graphs, dp, trees, strings, hashing") String topic,
            @ToolParam(description = "EASY, MEDIUM or HARD") String difficulty) {
        String d = difficulty == null ? "" : difficulty.trim().toUpperCase();
        if (!DIFFICULTIES.contains(d)) {
            throw new IllegalArgumentException("Difficulty must be EASY, MEDIUM or HARD.");
        }
        if (title == null || title.isBlank() || topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("Both title and topic are required.");
        }
        DsaProblem p = new DsaProblem();
        p.setTitle(title.trim());
        p.setTopic(topic.trim().toLowerCase());
        p.setDifficulty(d);
        p = problems.save(p);
        return new ProblemView(p.getId(), p.getTitle(), p.getTopic(), p.getDifficulty());
    }

    @Tool(description = "Show how many DSA problems the user has solved in total and per topic.")
    public DsaStats getDsaStats() {
        Map<String, Long> byTopic = new TreeMap<>();
        long total = 0;
        for (DsaProblemRepository.TopicCount t : problems.countByTopic()) {
            byTopic.put(t.getTopic(), t.getTotal());
            total += t.getTotal();
        }
        return new DsaStats(total, byTopic);
    }

    @Tool(description = "Find the user's weakest DSA topics, meaning the core topics with the fewest solved problems. "
            + "Topics with zero solved problems are included.")
    public List<TopicCountView> findWeakestTopics(
            @ToolParam(description = "How many topics to return, default 3", required = false) Integer limit) {
        int n = (limit == null || limit < 1) ? 3 : Math.min(limit, CORE_TOPICS.size());
        Map<String, Long> solved = problems.countByTopic().stream()
                .collect(Collectors.toMap(DsaProblemRepository.TopicCount::getTopic, DsaProblemRepository.TopicCount::getTotal));
        return CORE_TOPICS.stream()
                .map(t -> new TopicCountView(t, solved.getOrDefault(t, 0L)))
                .sorted(Comparator.comparingLong(TopicCountView::solved).thenComparing(TopicCountView::topic))
                .limit(n)
                .toList();
    }

    // ---------------------------------------------------------------- reminders

    @Tool(description = "Create a reminder, such as an assessment date or a deadline to apply.")
    @RequiresConfirmation
    public ReminderView addReminder(
            @ToolParam(description = "What to be reminded about") String text,
            @ToolParam(description = "Due date in YYYY-MM-DD format") String dueDate) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Reminder text is required.");
        }
        LocalDate date;
        try {
            date = LocalDate.parse(dueDate == null ? "" : dueDate.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Due date must look like 2026-11-30.");
        }
        Reminder r = new Reminder();
        r.setText(text.trim());
        r.setDueDate(date);
        r = reminders.save(r);
        return new ReminderView(r.getId(), r.getText(), r.getDueDate().toString());
    }

    @Tool(description = "List reminders that are due in the next N days, soonest first.")
    public List<ReminderView> listUpcomingReminders(
            @ToolParam(description = "Number of days to look ahead, default 14", required = false) Integer days) {
        int n = (days == null || days < 1) ? 14 : Math.min(days, 365);
        LocalDate today = LocalDate.now();
        return reminders.findByDueDateBetweenOrderByDueDate(today, today.plusDays(n)).stream()
                .map(r -> new ReminderView(r.getId(), r.getText(), r.getDueDate().toString()))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private static ApplicationStatus parseStatus(String raw) {
        try {
            return ApplicationStatus.valueOf(raw.trim().toUpperCase().replace(' ', '_'));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Status must be one of "
                    + Arrays.toString(ApplicationStatus.values()));
        }
    }
}
