package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.DeleteById;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.client.search.SearchClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.service.IInteractionReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author theshy
 * @since 2026-11-05
 */
@Service
@RequiredArgsConstructor
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    private final InteractionReplyMapper replyMapper;

    private final UserClient userClient;

    private final SearchClient searchClient;
    private final CatalogueClient catalogueClient;
    private final CourseClient courseClient;
    private final CategoryCache categoryCache;

    @Override
    public void saveQuestion(QuestionFormDTO questionDTO) {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 数据封装
        InteractionQuestion question = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        question.setUserId(userId);
        // 写入数据库
        save(question);
    }

    /**
     * VO里需要两类人的信息，一类是提问者的信息，一类是回答者的信息
     * 但是回答者在回答map里只能查到回答者id，没有回答者name，因此需要拿着回答者id去userMap里找
     * userIds集合是存放所有需要人的id，既包括提问者的id，也包括回答者的id
     * 根据userIds集合可以找到users，进而封装为userMap
     * 到时候需要提问者信息，就拿着提问者id去userMap里找，需要回答者信息，就拿着回答者id去userMap里找
     * @param query
     * @return
     */
    @Override
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query) {
        // 参数校验，课程id和小节id不能同时为空
        Long courseId = query.getCourseId();
        Long sectionId = query.getSectionId();
        if(courseId == null && sectionId == null){
            throw new BadRequestException("课程id和小节id不能都为空");
        }
        // 分页查询
        Page<InteractionQuestion> page = lambdaQuery()
                .select(InteractionQuestion.class ,info -> !info.getProperty().equals("description"))
                .eq(Boolean.TRUE.equals(query.getOnlyMine()), InteractionQuestion::getUserId, UserContext.getUser())
                .eq(courseId != null, InteractionQuestion::getCourseId, courseId)
                .eq(sectionId != null, InteractionQuestion::getSectionId, sectionId)
                .eq(InteractionQuestion::getHidden, false)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        // 根据user_id和latest_answer_id分别查询提问者和最近一次回答的信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        for (InteractionQuestion q : records) {
            if(!q.getAnonymity()){
                // 只查询非匿名的问题
                userIds.add(q.getUserId());
            }
            answerIds.add(q.getLatestAnswerId());
        }
        // 根据id查询最近一次回答
        answerIds.remove(null);
        Map<Long,InteractionReply> replyMap = new HashMap<>(answerIds.size());
        if(CollUtils.isNotEmpty(answerIds)){
            List<InteractionReply> replies = replyMapper.selectBatchIds(answerIds);
            for (InteractionReply reply : replies) {
                replyMap.put(reply.getId(),reply);
                if(!reply.getAnonymity()){
                    userIds.add(reply.getUserId());
                }
            }
        }

        // 根据id查询提问者信息
        userIds.remove(null);
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(CollUtils.isNotEmpty(userIds)){
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream()
                    .collect(Collectors.toMap(UserDTO::getId, u -> u));
        }

        // 封装VO
        List<QuestionVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion r : records) {
            // 将PO拷贝到VO
            QuestionVO vo = BeanUtils.copyBean(r, QuestionVO.class);
            voList.add(vo);
            // 封装提问者信息
            if(!r.getAnonymity()){
                UserDTO userDTO = userMap.get(r.getUserId());
                if(userDTO != null){
                    vo.setUserName(userDTO.getName());
                    vo.setUserIcon(userDTO.getIcon());
                }
            }
            // 封装最近一次回答的信息
            InteractionReply reply = replyMap.get(r.getLatestAnswerId());
            if(reply != null){
                vo.setLatestReplyContent(reply.getContent());
                if(!reply.getAnonymity()){
                    UserDTO user = userMap.get(reply.getUserId());
                    if(user != null){
                        vo.setLatestReplyUser(user.getName());
                    }
                }
            }
        }

        return PageDTO.of(page,voList);
    }

    @Override
    public QuestionVO queryQuestionById(Long id) {
        // 根据id查询数据
        InteractionQuestion question = getById(id);
        // 数据校验
        if(question == null || question.getHidden()){
            return null;
        }
        // 查询提问者信息
        UserDTO user = null;
        if(!question.getAnonymity()){
            user = userClient.queryUserById(question.getUserId());
        }
        // 封装VO
        QuestionVO vo = BeanUtils.copyBean(question, QuestionVO.class);
        if(user != null){
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }

        return vo;
    }


    @Override
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query) {
        // 处理课程名称，得到课程id
        List<Long> courseIds = null;
        if(StringUtils.isNotBlank(query.getCourseName())){
            courseIds = searchClient.queryCoursesIdByName(query.getCourseName());
            if(CollUtils.isEmpty(courseIds)){
                return PageDTO.empty(0L,0L);
            }
        }

        // 分页查询
        Integer status = query.getStatus();
        LocalDateTime begin = query.getBeginTime();
        LocalDateTime end = query.getEndTime();
        Page<InteractionQuestion> page = lambdaQuery()
                .in(courseIds != null, InteractionQuestion::getCourseId, courseIds)
                .eq(status != null, InteractionQuestion::getStatus, status)
                .gt(begin != null, InteractionQuestion::getCreateTime, begin)
                .lt(end != null, InteractionQuestion::getCreateTime, end)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        List<InteractionQuestion> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        // 准备VO需要的数据:用户数据、课程数据、章节数据
        Set<Long> userIds = new HashSet<>();
        Set<Long> cIds = new HashSet<>();
        Set<Long> cataIds = new HashSet<>();
        // 获取各种数据的id集合
        for (InteractionQuestion q : records) {
            userIds.add(q.getUserId());
            cIds.add(q.getCourseId());
            cataIds.add(q.getChapterId());
            cataIds.add(q.getSectionId());
        }
        // 根据id查询用户
        List<UserDTO> users = userClient.queryUserByIds(userIds);
        Map<Long,UserDTO> userMap = new HashMap<>(users.size());
        if(CollUtils.isNotEmpty(users)){
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId,u -> u));
        }
        // 根据id查询课程
        List<CourseSimpleInfoDTO> cInfos = courseClient.getSimpleInfoList(cIds);
        Map<Long,CourseSimpleInfoDTO> cInfoMap = new HashMap<>(cInfos.size());
        if(CollUtils.isNotEmpty(cInfos)){
            cInfoMap = cInfos.stream().collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        }
        // 根据id查询章节
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(cataIds);
        Map<Long,String> cataMap = new HashMap<>(catas.size());
        if(CollUtils.isNotEmpty(catas)){
            cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId,CataSimpleInfoDTO::getName));
        }

        // 封装VO
        List<QuestionAdminVO> voList = new ArrayList<>(records.size());
        for (InteractionQuestion q : records) {
            // 将PO转换成VO，属性拷贝
            QuestionAdminVO vo = BeanUtils.copyBean(q, QuestionAdminVO.class);
            voList.add(vo);
            // 用户信息
            UserDTO user = userMap.get(q.getUserId());
            if(user != null){
                vo.setUserName(user.getName());
            }
            // 课程信息及分类信息
            CourseSimpleInfoDTO cInfo = cInfoMap.get(q.getCourseId());
            if(cInfo != null){
                vo.setCourseName(cInfo.getName());
                vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            }
            // 章节信息
            vo.setChapterName(cataMap.getOrDefault(q.getChapterId(),""));
            vo.setSectionName(cataMap.getOrDefault(q.getSectionId(),""));
        }

        return PageDTO.of(page,voList);
    }

    @Override
    public QuestionAdminVO queryQuestionByIdAdmin(Long id) {
        // 根据id查询问题
        InteractionQuestion question = getById(id);
        if(question == null){
            return null;
        }

        // 将PO转换成VO
        QuestionAdminVO vo = BeanUtils.copyBean(question, QuestionAdminVO.class);

        // 查询提问者信息
        UserDTO user = userClient.queryUserById(question.getUserId());
        if(user != null){
            vo.setUserName(user.getName());
            vo.setUserIcon(user.getIcon());
        }

        // 查询课程信息
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(question.getCourseId(), false, true);
        if(cInfo != null){
            // 课程信息
            vo.setCourseName(cInfo.getName());
            // 分类信息
            vo.setCategoryName(categoryCache.getCategoryNames(cInfo.getCategoryIds()));
            // 教师信息
            List<Long> teacherIds = cInfo.getTeacherIds();
            List<UserDTO> teachers = userClient.queryUserByIds(teacherIds);
            if(CollUtils.isNotEmpty(teachers)){
                vo.setTeacherName(teachers.stream()
                        .map(UserDTO::getName).collect(Collectors.joining("/")));
            }
        }

        // 查询章节信息
        List<CataSimpleInfoDTO> catas = catalogueClient.batchQueryCatalogue(List.of(question.getChapterId(), question.getSectionId()));
        Map<Long,String> cataMap = new HashMap<>(catas.size());
        if(CollUtils.isNotEmpty(catas)){
            cataMap = catas.stream().collect(Collectors.toMap(CataSimpleInfoDTO::getId,CataSimpleInfoDTO::getName));
        }
        vo.setChapterName(cataMap.getOrDefault(question.getChapterId(),""));
        vo.setSectionName(cataMap.getOrDefault(question.getSectionId(),""));

        // 封装VO
        return vo;
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO questionDTO) {
        // 获取当前用户信息
        Long userId = UserContext.getUser();
        // 根据id查询提问
        InteractionQuestion question = getById(id);
        if(question == null){
            throw new BadRequestException("问题不存在");
        }
        // 判断问题的提问者是否是当前用户
        if(!question.getUserId().equals(userId)){
            throw new BadRequestException("无权修改他人问题");
        }
        // 修改问题
        InteractionQuestion q = BeanUtils.copyBean(questionDTO, InteractionQuestion.class);
        q.setId(id);
        updateById(question);
    }

    @Override
    public void deleteQuestion(Long id) {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 根据id查询问题
        InteractionQuestion question = getById(id);
        if(question == null){
            return;
        }
        // 判断是否是当前用户的问题
        if(!question.getUserId().equals(userId)){
            throw new BadRequestException("无权删除他人问题");
        }
        // 删除问题
        removeById(id);
        // 删除问题下的回答
        replyMapper.delete(new QueryWrapper<InteractionReply>().lambda().eq(InteractionReply::getQuestionId,id));
    }

    @Override
    public void hiddenQuestion(Long id, Boolean hidden) {
        InteractionQuestion question = new InteractionQuestion();
        question.setId(id);
        question.setHidden(hidden);
        updateById(question);
    }


}
