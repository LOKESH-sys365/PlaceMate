package com.placemate.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByDueDateBetweenOrderByDueDate(LocalDate from, LocalDate to);
}
