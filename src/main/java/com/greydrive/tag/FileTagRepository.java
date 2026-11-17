package com.greydrive.tag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FileTagRepository extends JpaRepository<FileTag, Long> {

    List<FileTag> findByFileId(Long fileId);

    boolean existsByFileIdAndTagId(Long fileId, Long tagId);

    void deleteByFileId(Long fileId);

    void deleteByTagId(Long tagId);

    @Query("select ft.fileId from FileTag ft where ft.tagId = :tagId")
    List<Long> findFileIdsByTagId(@Param("tagId") Long tagId);
}
