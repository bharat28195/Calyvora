package com.calyvora.feed;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One person's one reaction to a post. The composite key (post, user, emoji) is what makes reacting
 * idempotent — clicking 👍 twice can't produce two of them.
 */
@Entity
@Table(name = "post_reactions")
public class PostReaction {

    @EmbeddedId
    private Key key;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PostReaction() {
    }

    public PostReaction(UUID postId, UUID userId, String emoji, UUID companyId) {
        this.key = new Key(postId, userId, emoji);
        this.companyId = companyId;
        this.createdAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getPostId() { return key.postId; }
    public UUID getUserId() { return key.userId; }
    public String getEmoji() { return key.emoji; }

    /** Composite primary key: one reaction per person per emoji per post. */
    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "post_id", nullable = false)
        private UUID postId;

        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(nullable = false, length = 16)
        private String emoji;

        protected Key() {
        }

        Key(UUID postId, UUID userId, String emoji) {
            this.postId = postId;
            this.userId = userId;
            this.emoji = emoji;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key other)) return false;
            return Objects.equals(postId, other.postId)
                    && Objects.equals(userId, other.userId)
                    && Objects.equals(emoji, other.emoji);
        }

        @Override
        public int hashCode() {
            return Objects.hash(postId, userId, emoji);
        }
    }
}
