package com.matjom.matjom.feed.repository;

import com.matjom.matjom.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserReadRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u.name FROM User u WHERE u.id = :userId")
    Optional<String> findNameById(@Param("userId") UUID userId); // 9월 26일 최종: 리뷰 응답용 이름 조회
}
