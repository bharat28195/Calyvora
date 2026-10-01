package com.calyvora.feed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Reactions are keyed on (post, user, emoji), so the finders reach through the embedded key. */
public interface PostReactionRepository extends JpaRepository<PostReaction, PostReaction.Key> {

    List<PostReaction> findByKeyPostIdIn(List<UUID> postIds);

    List<PostReaction> findByKeyPostId(UUID postId);
}
