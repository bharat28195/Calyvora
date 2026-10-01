package com.calyvora.knowledge;

import com.calyvora.common.error.ConflictException;
import com.calyvora.common.error.NotFoundException;
import com.calyvora.common.security.AuthPrincipal;
import com.calyvora.common.security.TenantContext;
import com.calyvora.knowledge.dto.CreateSpaceRequest;
import com.calyvora.knowledge.dto.SpaceResponse;
import com.calyvora.knowledge.dto.UpdateSpaceRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Spaces (Knowledge OS slice K1). Any member can create/edit; archiving is gated at the controller. */
@Service
public class SpaceService {

    private final SpaceRepository spaceRepository;
    private final PageRepository pageRepository;

    public SpaceService(SpaceRepository spaceRepository, PageRepository pageRepository) {
        this.spaceRepository = spaceRepository;
        this.pageRepository = pageRepository;
    }

    @Transactional(readOnly = true)
    public List<SpaceResponse> list() {
        UUID companyId = TenantContext.getCompanyId();
        return spaceRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public SpaceResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public SpaceResponse create(CreateSpaceRequest request, AuthPrincipal principal) {
        UUID companyId = TenantContext.getCompanyId();
        String key = request.key().trim().toUpperCase(Locale.ROOT);
        if (spaceRepository.existsByCompanyIdAndKeyIgnoreCase(companyId, key)) {
            throw new ConflictException("A space with that key already exists");
        }
        Space space = new Space(UUID.randomUUID(), companyId, request.name().trim(), key,
                blankToNull(request.description()), principal.userId());
        spaceRepository.save(space);
        return toResponse(space);
    }

    @Transactional
    public SpaceResponse update(UUID id, UpdateSpaceRequest request) {
        Space space = require(id);
        if (request.name() != null && !request.name().isBlank()) {
            space.setName(request.name().trim());
        }
        if (request.description() != null) {
            space.setDescription(blankToNull(request.description()));
        }
        return toResponse(space);
    }

    @Transactional
    public SpaceResponse archive(UUID id) {
        Space space = require(id);
        space.setStatus(SpaceStatus.ARCHIVED);
        return toResponse(space);
    }

    // ---- helpers ----

    private Space require(UUID id) {
        UUID companyId = TenantContext.getCompanyId();
        return spaceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new NotFoundException("Space not found"));
    }

    private SpaceResponse toResponse(Space s) {
        return SpaceResponse.of(s, pageRepository.countBySpaceId(s.getId()));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
