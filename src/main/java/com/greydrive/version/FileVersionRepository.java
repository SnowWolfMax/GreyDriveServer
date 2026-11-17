package com.greydrive.version;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FileVersionRepository extends JpaRepository<FileVersion, Long> {

    List<FileVersion> findByFileIdOrderByVersionNoDesc(Long fileId);

    Optional<FileVersion> findByFileIdAndVersionNo(Long fileId, int versionNo);

    @Query("select coalesce(max(v.versionNo), 0) from FileVersion v where v.fileId = :fileId")
    int maxVersionNo(@Param("fileId") Long fileId);

    void deleteByFileId(Long fileId);
}
