package com.matjom.matjom.feed.repository;

import com.matjom.matjom.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 사용자 조회 전용 리포지토리.
 * 사용 목적: 리뷰/좋아요 응답에 필요한 사용자 정보를 가볍게 조회한다.
 * 코드 의미: Spring Data JPA를 사용해 UUID 기반 사용자 데이터를 읽는다.
 * 기대 결과: 서비스가 전체 엔티티를 로딩하지 않고도 이름 등을 받아올 수 있다.
 */
public interface UserReadRepository extends JpaRepository<User, UUID> {

    @Query("SELECT u.name FROM User u WHERE u.id = :userId")
    /**
     * 사용자 UUID로 이름만 조회한다.
     * 사용 목적: 리뷰 응답에서 userId 대신 실명의 텍스트를 노출한다.
     * 코드 의미: 전체 엔티티를 로딩하지 않고 name 필드만 선택해 Optional로 감싼다.
     * 기대 결과: 사용자 이름이 존재하면 Optional에 포함되어 응답 구성에 활용된다.
     */
    Optional<String> findNameById(@Param("userId") UUID userId);
}
