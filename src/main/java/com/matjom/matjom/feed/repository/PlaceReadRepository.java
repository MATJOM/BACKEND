package com.matjom.matjom.feed.repository;

import com.matjom.matjom.place.entity.Place;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceReadRepository extends JpaRepository<Place, Long> {

    @Query("SELECT p.name FROM Place p WHERE p.id = :placeId")
    Optional<String> findNameById(@Param("placeId") Long placeId); // 9월 26일 최종: 리뷰 응답용 장소 이름
}
