package com.tianji.gateway.filter;

import com.tianji.authsdk.gateway.util.AuthUtil;
import com.tianji.common.domain.R;
import com.tianji.common.domain.dto.LoginUserDTO;
import com.tianji.gateway.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static com.tianji.auth.common.constants.JwtConstants.AUTHORIZATION_HEADER;
import static com.tianji.auth.common.constants.JwtConstants.USER_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountAuthFilterTest {

    @Test
    void forwardsAuthenticatedUserToDownstreamService() {
        AuthUtil authUtil = mock(AuthUtil.class);
        AuthProperties properties = new AuthProperties();
        properties.setExcludePath(Collections.emptySet());
        AccountAuthFilter filter = new AccountAuthFilter(authUtil, properties);
        LoginUserDTO user = new LoginUserDTO();
        user.setUserId(2L);
        when(authUtil.parseToken("test-token")).thenReturn(R.ok(user));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/cs/courses/3/catalogs")
                        .header(AUTHORIZATION_HEADER, "test-token"));

        filter.filter(exchange, chain).block();

        ArgumentCaptor<ServerWebExchange> forwarded = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(forwarded.capture());
        assertEquals("2", forwarded.getValue().getRequest().getHeaders().getFirst(USER_HEADER));
    }
}
