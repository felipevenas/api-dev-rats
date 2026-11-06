package com.devrats.services;

import com.devrats.services.email.PasswordResetEmail;

public interface EmailService {

    void sendPasswordResetEmail(PasswordResetEmail email);
}
