package com.devrats.enums;

public enum VerificationMode {
    SELECTED_REPOSITORIES,
    OWN_REPOSITORIES,
    STUDY_PHOTO;

    //---- Modos de commit são a única fonte de dia válido por atividade no GitHub.
    public boolean countsCommits() {
        return this == SELECTED_REPOSITORIES || this == OWN_REPOSITORIES;
    }
}
