package com.calyvora.knowledge;

import com.calyvora.common.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Who may publish company documents: the handbook, policies and files everyone in the company reads.
 *
 * <p>The knowledge base used to be a wiki: any employee could create a space, write a page or edit
 * someone else's. As the company's documents area that is wrong in a way that matters: a leave policy
 * an employee can edit is not a policy. So reading is everyone's and writing is a publisher's, and
 * drafts are shown only to publishers until they are published.
 *
 * <p>One method, used from {@code @PreAuthorize("@documentAccess.canPublish()")} and from the
 * services that filter drafts, so the rule lives in exactly one place. It is the seam the custom
 * roles work replaces with a permission.
 */
@Component("documentAccess")
public class DocumentAccess {

    private static final Set<String> PUBLISHERS = Set.of("OWNER", "ADMIN", "HR");

    /** @return whether the current caller may create, edit, publish, upload or delete documents. */
    public boolean canPublish() {
        return canPublish(current());
    }

    public boolean canPublish(AuthPrincipal principal) {
        return principal != null && PUBLISHERS.contains(principal.role());
    }

    private static AuthPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }
}
