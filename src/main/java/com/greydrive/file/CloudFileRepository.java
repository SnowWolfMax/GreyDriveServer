package com.greydrive.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CloudFileRepository extends JpaRepository<CloudFile, Long>, JpaSpecificationExecutor<CloudFile> {

    Optional<CloudFile> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    Optional<CloudFile> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndFolderIdAndNameAndDeletedFalse(Long userId, Long folderId, String name);

    boolean existsByUserIdAndFolderIdIsNullAndNameAndDeletedFalse(Long userId, String name);

    List<CloudFile> findByUserIdAndFolderIdAndDeletedFalse(Long userId, Long folderId);

    List<CloudFile> findByUserIdAndFolderIdIsNullAndDeletedFalse(Long userId);

    List<CloudFile> findByUserIdAndFolderId(Long userId, Long folderId);

    List<CloudFile> findByUserIdAndDeletedTrue(Long userId);

    List<CloudFile> findByDeletedTrueAndDeletedAtBefore(java.time.Instant cutoff);

    Page<CloudFile> findByUserIdAndDeletedTrue(Long userId, Pageable pageable);

    Page<CloudFile> findByUserIdAndFavoriteTrueAndDeletedFalse(Long userId, Pageable pageable);

    Optional<CloudFile> findFirstByUserIdAndSha256AndDeletedFalse(Long userId, String sha256);

    Optional<CloudFile> findFirstBySha256(String sha256);

    long countByUserIdAndDeletedFalse(Long userId);

    @Query("select coalesce(sum(f.size), 0) from CloudFile f where f.userId = :userId and f.deleted = false")
    long sumActiveSize(Long userId);

    @Query("select coalesce(sum(f.size), 0) from CloudFile f where f.deleted = false")
    long sumAllActiveSize();

    long countByDeletedFalse();
}
