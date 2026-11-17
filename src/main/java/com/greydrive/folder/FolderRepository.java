package com.greydrive.folder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    Page<Folder> findByUserIdAndParentIdAndDeletedFalse(Long userId, Long parentId, Pageable pageable);

    Page<Folder> findByUserIdAndParentIdIsNullAndDeletedFalse(Long userId, Pageable pageable);

    Page<Folder> findByUserIdAndDeletedTrue(Long userId, Pageable pageable);

    List<Folder> findByUserIdAndDeletedTrue(Long userId);

    List<Folder> findByUserIdAndParentId(Long userId, Long parentId);

    List<Folder> findByDeletedTrueAndDeletedAtBefore(java.time.Instant cutoff);

    Optional<Folder> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    Optional<Folder> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndParentIdAndNameAndDeletedFalse(Long userId, Long parentId, String name);

    boolean existsByUserIdAndParentIdIsNullAndNameAndDeletedFalse(Long userId, String name);

    List<Folder> findByUserIdAndParentIdAndDeletedFalse(Long userId, Long parentId);

    List<Folder> findByUserIdAndParentIdIsNullAndDeletedFalse(Long userId);

    long countByUserIdAndDeletedFalse(Long userId);
}
