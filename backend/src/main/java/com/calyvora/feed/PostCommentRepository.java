package com.calyvora.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    List<PostComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    /** One query for a page of posts, rather than one per post. */
    List<PostComment> findByPostIdInOrderByCreatedAtAsc(List<UUID> postIds);

    Optional<PostComment> findByIdAndCompanyId(UUID id, UUID companyId);
}
