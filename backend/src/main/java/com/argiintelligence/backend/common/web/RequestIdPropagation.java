package com.argiintelligence.backend.common.web;

import org.springframework.http.client.ClientHttpRequestInterceptor;

/** Forwards the current correlation id on outgoing calls to ML and data providers (MASTER_SPEC D15). */
public final class RequestIdPropagation {

    private RequestIdPropagation() {
    }

    public static ClientHttpRequestInterceptor interceptor() {
        return (request, body, execution) -> {
            String id = RequestIdFilter.current();
            if (id != null) {
                request.getHeaders().set(RequestIdFilter.HEADER, id);
            }
            return execution.execute(request, body);
        };
    }
}
