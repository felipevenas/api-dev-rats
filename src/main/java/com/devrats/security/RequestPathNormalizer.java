package com.devrats.security;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/**
 * Normaliza o path para casar buckets de rate limit e cache-control com o
 * mesmo recurso que o Spring MVC vai despachar.
 *
 * <p>Remove parâmetros de matriz ({@code ;x=1}) e percent-encoding. Decodificar
 * {@code %2F} pode ampliar o match, mas para rate limit/cache isso é fail-safe:
 * o pior caso é aplicar a regra privada/restritiva a um path que já seria
 * roteado para o mesmo recurso.</p>
 */
public final class RequestPathNormalizer {

    private RequestPathNormalizer() {
    }

    public static String normalize(String path) {
        String withoutMatrixParams = path.replaceAll(";[^/]*", "");
        try {
            return UriUtils.decode(withoutMatrixParams, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return withoutMatrixParams;
        }
    }
}
