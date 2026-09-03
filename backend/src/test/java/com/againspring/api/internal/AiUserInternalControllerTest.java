package com.againspring.api.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiUserInternalControllerTest {
    @Test
    void guardRejectsBlankTokenAndWrongHeader() {
        assertFalse(new AiUserInternalTokenGuard("").isAuthorized("Bearer x"));
        assertFalse(new AiUserInternalTokenGuard("secret").isAuthorized("Bearer wrong"));
        assertFalse(new AiUserInternalTokenGuard("secret").isAuthorized(null));
        assertTrue(new AiUserInternalTokenGuard("secret").isAuthorized("Bearer secret"));
    }
}
