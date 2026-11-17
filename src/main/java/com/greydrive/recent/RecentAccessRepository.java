package com.greydrive.recent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RecentAccessRepository extends JpaRepository<RecentAccess, Long> {

    Optional<RecentAccess> findByUserIdAndFileId(Long userId, Long fileId);

    @Query("select r from RecentAccess r where r.userId = :userId order by r.accessedAt desc")
    Page<RecentAccess> pageByUser(@Param("userId") Long userId, Pageable pageable);

    void deleteByUserId(Long userId);

    void deleteByFileId(Long fileId);
}
