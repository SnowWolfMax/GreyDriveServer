package com.greydrive.share;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShareRepository extends JpaRepository<Share, Long> {

    Optional<Share> findByTokenAndStatus(String token, String status);

    Optional<Share> findByIdAndUserId(Long id, Long userId);

    Page<Share> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
