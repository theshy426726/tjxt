package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionReplyMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InteractionQuestionServiceImplTest {
    private InteractionQuestionServiceImpl service;
    private UserClient userClient;
    private InteractionReplyMapper replyMapper;
    private LambdaQueryChainWrapper<InteractionQuestion> questionQuery;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userClient = mock(UserClient.class);
        replyMapper = mock(InteractionReplyMapper.class);
        service = spy(new InteractionQuestionServiceImpl(replyMapper, userClient,
                mock(SearchClient.class), mock(CatalogueClient.class),
                mock(CourseClient.class), mock(CategoryCache.class)));
        questionQuery = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(questionQuery).when(service).lambdaQuery();
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void coursePageWithoutOnlyMineReturnsQuestions() {
        InteractionQuestion question = question();
        stubPage(question);
        QuestionPageQuery query = new QuestionPageQuery();
        query.setCourseId(10L);

        PageDTO<QuestionVO> result = service.queryQuestionPage(query);

        assertEquals(1L, result.getTotal());
        assertEquals(100L, result.getList().get(0).getId());
        assertEquals("Question title", result.getList().get(0).getTitle());
    }

    @Test
    void onlyMineStillFiltersByCurrentUser() {
        UserContext.setUser(20L);
        stubPage(question());
        QuestionPageQuery query = new QuestionPageQuery();
        query.setCourseId(10L);
        query.setOnlyMine(true);

        service.queryQuestionPage(query);

        verify(questionQuery).eq(eq(true), any(), eq(20L));
    }

    @Test
    void missingLatestReplyUserDoesNotBreakQuestionPage() {
        InteractionQuestion question = question().setLatestAnswerId(300L);
        stubPage(question);
        InteractionReply reply = new InteractionReply();
        reply.setId(300L);
        reply.setUserId(30L);
        reply.setAnonymity(false);
        reply.setContent("Reply content");
        when(replyMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(reply));
        when(userClient.queryUserByIds(any())).thenReturn(Collections.emptyList());
        QuestionPageQuery query = new QuestionPageQuery();
        query.setCourseId(10L);
        query.setOnlyMine(false);

        QuestionVO result = service.queryQuestionPage(query).getList().get(0);

        assertEquals(100L, result.getId());
        assertEquals("Reply content", result.getLatestReplyContent());
        assertNull(result.getLatestReplyUser());
    }

    @Test
    void anonymousQuestionDetailContainsQuestionData() {
        doReturn(question()).when(service).getById(100L);

        QuestionVO result = service.queryQuestionById(100L);

        assertQuestionDetail(result);
        verifyNoInteractions(userClient);
    }

    @Test
    void questionDetailKeepsQuestionIdWhenAddingUserInfo() {
        doReturn(question().setAnonymity(false)).when(service).getById(100L);
        UserDTO user = new UserDTO();
        user.setId(20L);
        user.setName("Student");
        when(userClient.queryUserById(20L)).thenReturn(user);

        QuestionVO result = service.queryQuestionById(100L);

        assertQuestionDetail(result);
        assertEquals("Student", result.getUserName());
    }

    @Test
    void questionDetailSurvivesMissingUserInfo() {
        doReturn(question().setAnonymity(false)).when(service).getById(100L);

        assertQuestionDetail(service.queryQuestionById(100L));
    }

    private InteractionQuestion question() {
        return new InteractionQuestion().setId(100L).setCourseId(10L).setUserId(20L)
                .setTitle("Question title").setDescription("Question description")
                .setAnonymity(true).setHidden(false).setAnswerTimes(2);
    }

    private void stubPage(InteractionQuestion question) {
        Page<InteractionQuestion> page = new Page<>(1, 10, 1);
        page.setRecords(Collections.singletonList(question));
        doReturn(page).when(questionQuery).page(any(Page.class));
    }

    private void assertQuestionDetail(QuestionVO result) {
        assertNotNull(result);
        assertEquals(100L, result.getId());
        assertEquals("Question title", result.getTitle());
        assertEquals("Question description", result.getDescription());
        assertEquals(2, result.getAnswerTimes());
    }
}
