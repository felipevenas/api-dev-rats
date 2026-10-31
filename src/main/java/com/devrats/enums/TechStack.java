package com.devrats.enums;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Allowlist de tecnologias aceitas em {@code users.primary_stack}.
 *
 * <p>A coluna é persistida como CSV de nomes de enum ordenados alfabeticamente,
 * o que mantém a representação estável entre escritas. Use {@link #toCsv(Collection)}
 * e {@link #fromCsv(String)} em vez de converter manualmente.</p>
 */
public enum TechStack {
    JAVA,
    KOTLIN,
    JAVASCRIPT,
    TYPESCRIPT,
    PYTHON,
    GO,
    CSHARP,
    PHP,
    RUBY,
    RUST,
    SWIFT,
    REACT,
    NODE,
    SPRING,
    DOTNET,
    SQL,
    DOCKER,
    AWS;

    private static final Comparator<TechStack> BY_NAME = Comparator.comparing(TechStack::name);

    /**
     * Converte a lista em CSV ordenado e sem duplicatas.
     *
     * @return {@code null} quando não há itens, para que a coluna fique nula
     */
    public static String toCsv(Collection<TechStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return null;
        }
        String csv = stacks.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted(BY_NAME)
                .map(TechStack::name)
                .collect(Collectors.joining(","));
        return csv.isEmpty() ? null : csv;
    }

    /**
     * Lê o CSV persistido de volta para a lista de enums, sem duplicatas e ordenada.
     * Valores desconhecidos indicam corrupção de dados e lançam {@link IllegalArgumentException}.
     */
    public static List<TechStack> fromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .map(TechStack::valueOf)
                .distinct()
                .sorted(BY_NAME)
                .toList();
    }
}
