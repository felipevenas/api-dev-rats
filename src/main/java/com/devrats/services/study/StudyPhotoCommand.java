package com.devrats.services.study;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Comando de publicação de uma foto de estudo.
 *
 * <p>Repositório e URL de commit são contexto visual opcional e não participam
 * da pontuação do modo foto.</p>
 */
public record StudyPhotoCommand(
        MultipartFile file,
        String title,
        String description,
        UUID githubRepositoryId,
        String commitUrl
) {
}
