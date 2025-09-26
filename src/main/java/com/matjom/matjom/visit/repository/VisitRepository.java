package com.matjom.matjom.visit.repository;

import com.matjom.matjom.visit.entity.Visit;
import com.matjom.matjom.visit.entity.VisitState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VisitRepository extends JpaRepository<Visit, Long> {

    boolean existsByUser_IdAndState(UUID userId, VisitState state);

    Optional<Visit> findFirstByUser_IdAndState(UUID userId, VisitState state);
}
