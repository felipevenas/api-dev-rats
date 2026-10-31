package com.devrats.services.storage;

import com.devrats.config.AppProperties;
import com.devrats.exceptions.DomainException;
import com.devrats.exceptions.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class S3AvatarStorageTest {

    @Test
    void shouldBeUnavailableAndRejectUpload_whenCredentialsAreMissing() {
        S3AvatarStorage storage = new S3AvatarStorage(new AppProperties());

        assertThat(storage.isAvailable()).isFalse();
        assertThatThrownBy(() -> storage.upload("avatars/user/avatar.jpg", new byte[]{1}, "image/jpeg"))
                .isInstanceOfSatisfying(DomainException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AVATAR_STORAGE_UNAVAILABLE));
    }

    @Test
    void shouldIgnoreDelete_whenStorageIsUnavailable() {
        S3AvatarStorage storage = new S3AvatarStorage(new AppProperties());

        storage.delete("avatars/user/avatar.jpg");

        assertThat(storage.isAvailable()).isFalse();
    }
}
