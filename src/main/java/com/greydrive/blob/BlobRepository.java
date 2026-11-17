package com.greydrive.blob;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlobRepository extends JpaRepository<Blob, String> {

    @Query("select coalesce(sum(b.size), 0) from Blob b")
    long sumSize();

    @Modifying(flushAutomatically = true)
    @Query("update Blob b set b.refCount = b.refCount + 1 where b.sha256 = :sha")
    int incrementRef(@Param("sha") String sha256);

    @Modifying(flushAutomatically = true)
    @Query("update Blob b set b.refCount = b.refCount - 1 where b.sha256 = :sha")
    int decrementRef(@Param("sha") String sha256);

    @Query("select b.refCount from Blob b where b.sha256 = :sha")
    Integer findRefCount(@Param("sha") String sha256);
}
