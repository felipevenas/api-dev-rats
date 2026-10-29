package com.devstreak.utils;

import com.devstreak.exceptions.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyHelperTest {

    @Test
    void shouldAcceptStrongPassword() {
        PasswordPolicyHelper.validate("Str0ng!Passw0rd");
    }

    @Test
    void shouldRejectPasswordWithoutUppercase() {
        assertThatThrownBy(() -> PasswordPolicyHelper.validate("weak!passw0rd"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void shouldRejectShortPassword() {
        assertThatThrownBy(() -> PasswordPolicyHelper.validate("Aa1!short"))
                .isInstanceOf(ValidationException.class);
    }
}
