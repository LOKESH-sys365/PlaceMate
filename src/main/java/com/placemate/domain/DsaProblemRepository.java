package com.placemate.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DsaProblemRepository extends JpaRepository<DsaProblem, Long> {

    interface TopicCount {
        String getTopic();
        long getTotal();
    }

    @Query("select p.topic as topic, count(p) as total from DsaProblem p group by p.topic")
    List<TopicCount> countByTopic();
}
