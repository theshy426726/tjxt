package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionQuestionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InteractionReplyServiceImplTest {
    private InteractionReplyServiceImpl service;
    private LambdaQueryChainWrapper<InteractionReply> replyQuery;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RemarkClient remarkClient = mock(RemarkClient.class);
        when(remarkClient.isBizLiked(any())).thenReturn(Collections.emptySet());
        service = spy(new InteractionReplyServiceImpl(mock(IInteractionQuestionService.class),
                mock(UserClient.class), remarkClient));
        replyQuery = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(replyQuery).when(service).lambdaQuery();
    }

    @Test
    void questionIdAloneReturnsAnswers() {
        stubPage(0L);
        ReplyPageQuery query = new ReplyPageQuery();
        query.setQuestionId(1585089140469317634L);

        PageDTO<ReplyVO> result = service.queryReplyPage(query, false);

        assertEquals(1L, result.getTotal());
        assertEquals("Answer content", result.getList().get(0).getContent());
        verify(replyQuery).eq(eq(true), any(), eq(query.getQuestionId()));
        verify(replyQuery).eq(any(), eq(0L));
    }

    @Test
    void answerIdAloneReturnsComments() {
        stubPage(200L);
        ReplyPageQuery query = new ReplyPageQuery();
        query.setAnswerId(200L);

        PageDTO<ReplyVO> result = service.queryReplyPage(query, false);

        assertEquals(1L, result.getTotal());
        assertEquals("Answer content", result.getList().get(0).getContent());
        verify(replyQuery).eq(eq(false), any(), isNull());
        verify(replyQuery).eq(any(), eq(200L));
    }

    @Test
    void bothIdsMissingStillRejectsRequest() {
        assertThrows(BadRequestException.class,
                () -> service.queryReplyPage(new ReplyPageQuery(), false));
        verifyNoInteractions(replyQuery);
    }

    private void stubPage(Long answerId) {
        InteractionReply reply = new InteractionReply().setId(300L)
                .setQuestionId(1585089140469317634L).setAnswerId(answerId)
                .setAnonymity(true).setContent("Answer content");
        Page<InteractionReply> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(reply));
        doReturn(page).when(replyQuery).page(any(Page.class));
    }
}
