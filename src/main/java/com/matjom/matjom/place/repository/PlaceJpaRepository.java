package com.matjom.matjom.place.repository;

import com.matjom.matjom.place.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaceJpaRepository extends JpaRepository<Place, Long> {
}
