package com.greydrive.upload;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {

    Optional<UploadSession> findByIdAndUserId(String id, Long userId);

    List<UploadSession> findByStatusAndExpiresAtBefore(String status, Instant time);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from UploadSession s where s.id = :id")
    Optional<UploadSession> lockById(@Param("id") String id);
}
