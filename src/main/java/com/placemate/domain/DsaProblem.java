package com.placemate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "dsa_problem")
@Getter
@Setter
@NoArgsConstructor
public class DsaProblem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    /** Normalised to lower case, e.g. "graphs", "dp". */
    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String difficulty; // EASY | MEDIUM | HARD

    private LocalDate solvedOn = LocalDate.now();
}
