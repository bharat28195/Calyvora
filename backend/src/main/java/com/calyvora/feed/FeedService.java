package com.calyvora.feed;

import com.calyvora.common.error.ApiException;
import com.calyvora.common.error.ErrorCode;
import com.calyvora.common.error.ForbiddenException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.feed.dto.PostPayload;
import com.calyvora.feed.dto.PostResponse;
import com.calyvora.feed.dto.PostResponse.CommentResponse;
import com.calyvora.identity.User;
import com.calyvora.identity.UserRepository;
import com.calyvora.people.Department;
import com.calyvora.people.DepartmentRepository;
import com.calyvora.people.Employee;
import com.calyvora.people.EmployeeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The company feed (founder request: a posts page with visibility options). Anyone can post; a post
 * is either company-wide or limited to one department.
 *
 * <p>The visibility rule is enforced on <em>read</em>, not just hidden in the UI: a department post
 * is only returned to people in that department (plus the author and Owner/Admin). Storing the
 * department on the post rather than deriving it from the author means moving teams never
 * retroactively changes who could see something.
 */
@Service
public class FeedService {

    private static final Pageable PAGE = PageRequest.of(0, 60);

    private final PostRepository postRepository;
    private final PostCommentRepository commentRepository;
    private final PostReactionRepository reactionRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;

    public FeedService(PostRepository postRepository, PostCommentRepository commentRepository,
                       PostReactionRepository reactionRepository, UserRepository userRepository,
                       EmployeeRepository employeeRepository, DepartmentRepository departmentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
    }

    // ---- reading ----

    @Transactional(readOnly = true)
    public List<PostResponse> feed(AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        UUID myDepartment = departmentOf(companyId, principal.userId());
        boolean admin = isAdmin(principal);

        List<Post> visible = postRepository.findByCompanyIdOrderByPinnedDescCreatedAtDesc(companyId, PAGE)
                .stream()
                .filter(p -> canSee(p, principal.userId(), myDepartment, admin))
                .toList();
        if (visible.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = visible.stream().map(Post::getId).toList();
        Map<UUID, List<PostComment>> commentsByPost = new HashMap<>();
        for (PostComment c : commentRepository.findByPostIdInOrderByCreatedAtAsc(ids)) {
            commentsByPost.computeIfAbsent(c.getPostId(), k -> new ArrayList<>()).add(c);
        }
        Map<UUID, List<PostReaction>> reactionsByPost = new HashMap<>();
        for (PostReaction r : reactionRepository.findByKeyPostIdIn(ids)) {
            reactionsByPost.computeIfAbsent(r.getPostId(), k -> new ArrayList<>()).add(r);
        }

        Map<UUID, User> users = usersById(companyId);
        Map<UUID, String> departments = departmentNames(companyId);
        Map<UUID, Employee> employeesByUser = employeesByUser(companyId);

        return visible.stream()
                .map(p -> render(p, principal, admin, users, employeesByUser, departments,
                        commentsByPost.getOrDefault(p.getId(), List.of()),
                        reactionsByPost.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    // ---- writing ----

    @Transactional
    public PostResponse create(PostPayload p, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (p.body() == null || p.body().isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Write something first");
        }
        PostVisibility visibility = p.visibility() == null
                ? PostVisibility.COMPANY : PostVisibility.valueOf(p.visibility());

        UUID departmentId = null;
        if (visibility == PostVisibility.DEPARTMENT) {
            if (p.departmentId() == null || p.departmentId().isBlank()) {
                throw new ApiException(ErrorCode.VALIDATION_ERROR, "Pick a team for a team-only post");
            }
            departmentId = UUID.fromString(p.departmentId());
            departmentRepository.findByIdAndCompanyId(departmentId, companyId)
                    .orElseThrow(() -> new NotFoundException("Department not found"));
        }

        Post post = new Post(UUID.randomUUID(), companyId, principal.userId(),
                p.kind() == null ? PostKind.UPDATE : PostKind.valueOf(p.kind()),
                p.body().trim(), visibility, departmentId);
        postRepository.save(post);
        return renderOne(post, principal);
    }

    @Transactional
    public void delete(UUID postId, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Post post = require(postId, companyId);
        requireCanManage(post, principal);
        commentRepository.findByPostIdOrderByCreatedAtAsc(postId).forEach(commentRepository::delete);
        reactionRepository.findByKeyPostId(postId).forEach(reactionRepository::delete);
        postRepository.delete(post);
    }

    /** Pinning is an Owner/Admin call — it's the company noticeboard, not a personal one. */
    @Transactional
    public PostResponse setPinned(UUID postId, boolean pinned, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (!isAdmin(principal)) {
            throw new ForbiddenException("Only an owner or admin can pin a post");
        }
        Post post = require(postId, companyId);
        post.setPinned(pinned);
        return renderOne(post, principal);
    }

    // ---- reactions & comments ----

    /** Toggles: reacting again with the same emoji removes it. */
    @Transactional
    public PostResponse react(UUID postId, String emoji, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Post post = require(postId, companyId);
        requireCanSee(post, principal);
        String value = emoji == null || emoji.isBlank() ? "👍" : emoji.trim();

        PostReaction.Key key = new PostReaction.Key(postId, principal.userId(), value);
        if (reactionRepository.existsById(key)) {
            reactionRepository.deleteById(key);
        } else {
            reactionRepository.save(new PostReaction(postId, principal.userId(), value, companyId));
        }
        return renderOne(post, principal);
    }

    @Transactional
    public PostResponse comment(UUID postId, String body, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        Post post = require(postId, companyId);
        requireCanSee(post, principal);
        if (body == null || body.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Write something first");
        }
        commentRepository.save(new PostComment(UUID.randomUUID(), companyId, postId,
                principal.userId(), body.trim()));
        return renderOne(post, principal);
    }

    @Transactional
    public void deleteComment(UUID commentId, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        PostComment comment = commentRepository.findByIdAndCompanyId(commentId, companyId)
                .orElseThrow(() -> new NotFoundException("Comment not found"));
        if (!comment.getAuthorId().equals(principal.userId()) && !isAdmin(principal)) {
            throw new ForbiddenException("You can only delete your own comments");
        }
        commentRepository.delete(comment);
    }

    // ---- visibility ----

    /**
     * Who may see a post: everyone for a company post; for a department post, that department's
     * members, the author, and Owner/Admin.
     */
    private boolean canSee(Post post, UUID userId, UUID myDepartment, boolean admin) {
        if (post.getVisibility() == PostVisibility.COMPANY) {
            return true;
        }
        return admin
                || post.getAuthorId().equals(userId)
                || (myDepartment != null && myDepartment.equals(post.getDepartmentId()));
    }

    private void requireCanSee(Post post, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        if (!canSee(post, principal.userId(), departmentOf(companyId, principal.userId()), isAdmin(principal))) {
            throw new NotFoundException("Post not found");   // don't confirm a post the caller can't see
        }
    }

    private void requireCanManage(Post post, AuthPrincipal principal) {
        if (!post.getAuthorId().equals(principal.userId()) && !isAdmin(principal)) {
            throw new ForbiddenException("You can only delete your own posts");
        }
    }

    // ---- rendering ----

    private PostResponse renderOne(Post post, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        return render(post, principal, isAdmin(principal), usersById(companyId),
                employeesByUser(companyId), departmentNames(companyId),
                commentRepository.findByPostIdOrderByCreatedAtAsc(post.getId()),
                reactionRepository.findByKeyPostId(post.getId()));
    }

    private PostResponse render(Post post, AuthPrincipal principal, boolean admin, Map<UUID, User> users,
                                Map<UUID, Employee> employeesByUser, Map<UUID, String> departments,
                                List<PostComment> comments, List<PostReaction> reactions) {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<String> mine = new ArrayList<>();
        for (PostReaction r : reactions) {
            counts.merge(r.getEmoji(), 1L, Long::sum);
            if (r.getUserId().equals(principal.userId())) {
                mine.add(r.getEmoji());
            }
        }

        List<CommentResponse> rendered = comments.stream()
                .map(c -> new CommentResponse(c.getId().toString(), c.getAuthorId().toString(),
                        nameOf(users, c.getAuthorId()), c.getBody(),
                        c.getAuthorId().equals(principal.userId()) || admin,
                        c.getCreatedAt().toString()))
                .toList();

        Employee authorEmployee = employeesByUser.get(post.getAuthorId());
        return new PostResponse(post.getId().toString(), post.getAuthorId().toString(),
                nameOf(users, post.getAuthorId()),
                authorEmployee == null ? null : authorEmployee.getJobTitle(),
                post.getKind().name(), post.getBody(), post.getVisibility().name(),
                post.getDepartmentId() == null ? null : post.getDepartmentId().toString(),
                post.getDepartmentId() == null ? null : departments.get(post.getDepartmentId()),
                post.isPinned(), counts, mine, rendered,
                post.getAuthorId().equals(principal.userId()) || admin,
                post.getCreatedAt().toString());
    }

    // ---- lookups ----

    private Post require(UUID postId, UUID companyId) {
        return postRepository.findByIdAndCompanyId(postId, companyId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
    }

    private UUID departmentOf(UUID companyId, UUID userId) {
        return employeeRepository.findByUserId(userId)
                .filter(e -> e.getCompanyId().equals(companyId))
                .map(Employee::getDepartmentId)
                .orElse(null);
    }

    private Map<UUID, User> usersById(UUID companyId) {
        Map<UUID, User> users = new HashMap<>();
        for (User u : userRepository.findByCompanyIdOrderByCreatedAtAsc(companyId)) {
            users.put(u.getId(), u);
        }
        return users;
    }

    private Map<UUID, Employee> employeesByUser(UUID companyId) {
        Map<UUID, Employee> byUser = new HashMap<>();
        for (Employee e : employeeRepository.findByCompanyId(companyId)) {
            byUser.put(e.getUserId(), e);
        }
        return byUser;
    }

    private Map<UUID, String> departmentNames(UUID companyId) {
        Map<UUID, String> names = new HashMap<>();
        for (Department d : departmentRepository.findByCompanyIdOrderByName(companyId)) {
            names.put(d.getId(), d.getName());
        }
        return names;
    }

    private static String nameOf(Map<UUID, User> users, UUID userId) {
        User u = users.get(userId);
        return u == null ? "Someone" : (u.getFirstName() + " " + u.getLastName()).trim();
    }

    private static boolean isAdmin(AuthPrincipal principal) {
        return "OWNER".equals(principal.role()) || "ADMIN".equals(principal.role());
    }
}
