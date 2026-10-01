package com.calyvora.feed;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {

    /** Pinned first, then newest — the order the feed renders in. */
    List<Post> findByCompanyIdOrderByPinnedDescCreatedAtDesc(UUID companyId, Pageable pageable);

    Optional<Post> findByIdAndCompanyId(UUID id, UUID companyId);
}
