package edi.auth.web.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceContextFilterTest {

    private final TraceContextFilter filter = new TraceContextFilter();

    private String run(String incoming, AtomicReference<String> seenInMdc) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/users/me");
        if (incoming != null) {
            req.addHeader(TraceContextFilter.HEADER, incoming);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest r, ServletResponse s) {
                seenInMdc.set(MDC.get(TraceContextFilter.MDC_KEY));
            }
        });
        return res.getHeader(TraceContextFilter.HEADER);
    }

    @Test
    void propagaElIdRecibido() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();

        assertThat(run("abc-123", mdc)).isEqualTo("abc-123");
        assertThat(mdc.get()).isEqualTo("abc-123");
        assertThat(MDC.get(TraceContextFilter.MDC_KEY)).isNull();
    }

    @Test
    void sinIdOConCaracteresRaros_generaUnoNuevo() throws Exception {
        AtomicReference<String> mdc = new AtomicReference<>();

        assertThat(run(null, mdc)).hasSize(36);
        assertThat(run("x\ninyectado", mdc)).hasSize(36).doesNotContain("inyectado");
    }
}
