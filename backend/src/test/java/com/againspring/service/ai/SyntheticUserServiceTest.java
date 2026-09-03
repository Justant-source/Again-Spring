package com.againspring.service.ai;

import com.againspring.domain.User;
import com.againspring.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SyntheticUserServiceTest {

    private final UserRepository repo = mock(UserRepository.class);
    private final PasswordEncoder enc = mock(PasswordEncoder.class);
    private final SyntheticUserService svc = new SyntheticUserService(repo, enc);

    @Test
    void createsWhenAbsent() {
        when(repo.findById("p1")).thenReturn(Optional.empty());
        when(enc.encode("pw")).thenReturn("hash");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = svc.upsert(new SyntheticUserService.PersonaUpsertRequest("p1", "ai-user-001@againspring.internal", "닉", "pw"));
        assertEquals("CREATED", r.status());
        verify(repo).save(argThat(u -> u.isSynthetic() && "ACTIVE".equals(u.getStatus()) && "hash".equals(u.getPasswordHash())));
    }

    @Test
    void doesNotResurrectSoftDeleted() {
        User deleted = new User();
        deleted.setId("p2");
        deleted.setDeletedAt(Instant.now());
        when(repo.findById("p2")).thenReturn(Optional.of(deleted));
        var r = svc.upsert(new SyntheticUserService.PersonaUpsertRequest("p2", "e", "n", "pw"));
        assertEquals("DELETED_SKIPPED", r.status());
        verify(repo, never()).save(any());
    }

    @Test
    void updatesActiveRow() {
        User u = new User();
        u.setId("p3");
        u.setSynthetic(false);
        when(repo.findById("p3")).thenReturn(Optional.of(u));
        when(enc.encode("pw")).thenReturn("hash2");
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        var r = svc.upsert(new SyntheticUserService.PersonaUpsertRequest("p3", "e", "새닉", "pw"));
        assertEquals("UPDATED", r.status());
        assertTrue(u.isSynthetic());
        assertEquals("새닉", u.getNickname());
        assertEquals("hash2", u.getPasswordHash());
    }
}
