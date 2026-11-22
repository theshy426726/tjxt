package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDto;
import com.tianji.api.client.course.CatalogueClient;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.api.dto.course.CataSimpleInfoDTO;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.course.CourseSimpleInfoDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.domain.vo.LearningPlanVO;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.PlanStatus;
import com.tianji.learning.mapper.LearningLessonMapper;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 学生课程表 服务实现类
 * </p>
 *
 * @author theshy
 * @since 2026-10-29
 */
@Service
@RequiredArgsConstructor
public class LearningLessonServiceImpl extends ServiceImpl<LearningLessonMapper, LearningLesson> implements ILearningLessonService {


    private final CourseClient courseClient;

    private final CatalogueClient catalogueClient;

    private final LearningRecordMapper recordMapper;

    @Override
    public void addUserLessons(Long userId, List<Long> courseIds) {
        // 根据课程id查询课程相关信息
        List<CourseSimpleInfoDTO> cInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(cInfoList)){
            return;
        }
        List<LearningLesson> list = new ArrayList<>(cInfoList.size());
        for (CourseSimpleInfoDTO cInfo : cInfoList) {
            LearningLesson lesson = new LearningLesson();
            // 获取过期时间
            Integer validation = cInfo.getValidDuration();
            if(validation != null && validation > 0){
                LocalDateTime now = LocalDateTime.now();
                lesson.setCreateTime(now);
                lesson.setExpireTime(now.plusMonths(validation));
            }
            // 填充userId和courseId
            lesson.setUserId(userId);
            lesson.setCourseId(cInfo.getId());
            list.add(lesson);
        }
        // 批量新增
        saveBatch(list);
    }

    /**
    学习微服务，分页查询我的课表，传入的是query参数，包含pageNo,pageSize,sortBy,isAsc
    查询得到的是由learningLesson封装得到的Page参数，里面没有课程详细信息
    因此需要根据learningLesson中的courseId查询课程服务，得到CourseSimpleInfoDTO的集合
    现在我们有了learningLesson还有CourseSimpleInfoDTO，我们需要把这两个组成LearningLessonVO并封装返回
     **/
    @Override
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query) {
        // 获取当前用户
        Long userId = UserContext.getUser();
        // 分页查询
        Page<LearningLesson> page = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        // 查询课程信息
        Map<Long, CourseSimpleInfoDTO> courseInfoMap = queryCourseSimpleInfoList(records);

        // 封装VO进行返回
        List<LearningLessonVO> list = new ArrayList<>(records.size());
        // 循环遍历，将LearningLesson转化为LearningLessonVO
        for (LearningLesson r : records) {
            // 先进行属性拷贝
            LearningLessonVO vo = BeanUtils.copyProperties(r, LearningLessonVO.class);
            // 将课程详情信息进行填充
            CourseSimpleInfoDTO courseInfo = courseInfoMap.get(r.getCourseId());
            vo.setCourseName(courseInfo.getName());
            vo.setCourseCoverUrl(courseInfo.getCoverUrl());
            vo.setSections(courseInfo.getSectionNum());
            list.add(vo);
        }

        return PageDTO.of(page,list);
    }

    private @NonNull Map<Long, CourseSimpleInfoDTO> queryCourseSimpleInfoList(List<LearningLesson> records) {
        //   获取课程id集合
        Set<Long> courseIds = records.stream().map(LearningLesson::getCourseId).collect(Collectors.toSet());
        //   根据课程id查询课程详情信息
        List<CourseSimpleInfoDTO> courseInfoList = courseClient.getSimpleInfoList(courseIds);
        if(CollUtils.isEmpty(courseInfoList)){
            throw new BadRequestException("课程信息不存在");
        }
        //   把课程信息集合处理为Map，方便后面拷贝属性的时候根据key查询
        Map<Long, CourseSimpleInfoDTO> courseInfoMap = courseInfoList.stream()
                .collect(Collectors.toMap(CourseSimpleInfoDTO::getId, c -> c));
        return courseInfoMap;
    }

    @Override
    public LearningLessonVO queryMyCurrentLesson() {
        // 获取当前用户
        Long userId = UserContext.getUser();
        // 查询当前学习的课程PO
        LearningLesson lesson = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .orderByDesc(LearningLesson::getLatestLearnTime)
                .last("limit 1")
                .one();
        if(lesson == null){
            return null;
        }
        // 将PO拷贝到VO
        LearningLessonVO vo = BeanUtils.copyProperties(lesson, LearningLessonVO.class);
        // 开始封装VO数据，先填充courseName,courseCoverUrl,section
        CourseFullInfoDTO courseInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if(courseInfo == null){
            throw new BadRequestException("课程不存在");
        }
        vo.setCourseName(courseInfo.getName());
        vo.setCourseCoverUrl(courseInfo.getCoverUrl());
        vo.setSections(courseInfo.getSectionNum());
        // 再填充courseAmount
        Integer courseAmount = lambdaQuery().eq(LearningLesson::getUserId, userId).count();
        vo.setCourseAmount(courseAmount);
        // 最后填充latestSectionName,latestSectionIndex
        List<CataSimpleInfoDTO> cataInfos = catalogueClient.batchQueryCatalogue(CollUtils.singletonList(lesson.getLatestSectionId()));
        if(!CollUtils.isEmpty(cataInfos)){
            CataSimpleInfoDTO cataInfo = cataInfos.get(0);
            vo.setLatestSectionIndex(cataInfo.getCIndex());
            vo.setLatestSectionName(cataInfo.getName());
        }
        return vo;
    }

    @Override
    public void deleteCourseFromLesson(Long userId, Long courseId) {
        if(userId == null){
            userId = UserContext.getUser();
        }
        remove(buildUserIdAndCourseIdWrapper(userId, courseId));
    }

    @Override
    public Long isLessonValid(Long courseId) {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 查询当前用户的课表里是否有该课程
        LearningLesson lesson = getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
        if(lesson == null){
            return null;
        }

        return lesson.getId();
    }

    @Override
    public LearningLessonVO queryLessonByCourseId(Long courseId) {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 查询当前用户的课表里是否有该课程
        LearningLesson lesson = getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
        if(lesson == null){
            return null;
        }
        return BeanUtils.copyBean(lesson,LearningLessonVO.class);
    }

    @Override
    public Integer countLearningLessonByCourse(Long courseId) {
        return lambdaQuery().eq(LearningLesson::getCourseId,courseId)
                .in(LearningLesson::getStatus,LessonStatus.NOT_BEGIN,LessonStatus.LEARNING,LessonStatus.FINISHED)
                .count();
    }

    @Override
    public LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId) {
        return getOne(buildUserIdAndCourseIdWrapper(userId, courseId));
    }


    @Override
    public void createLearningPlan(Long courseId, Integer freq) {
        // 获取当前用户信息
        Long userId = UserContext.getUser();
        // 查询课表中指定课程有关的数据
        LearningLesson lesson = queryByUserIdAndCourseId(userId, courseId);
        if(lesson == null){
            throw new BadRequestException("课程信息不存在");
        }
        // 修改数据
        LearningLesson l = new LearningLesson();
        l.setId(lesson.getId());
        l.setWeekFreq(freq);
        if(lesson.getPlanStatus() == PlanStatus.NO_PLAN){
            l.setPlanStatus(PlanStatus.PLAN_RUNNING);
        }
        updateById(l);
    }


    @Override
    public LearningPlanPageVO queryMyPlans(PageQuery query) {
        LearningPlanPageVO result = new LearningPlanPageVO();
        // 1.获取当前用户
        Long userId = UserContext.getUser();
        // 2.获取本周起始时间
        LocalDate now = LocalDate.now();
        LocalDateTime begin = DateUtils.getWeekBeginTime(now);
        LocalDateTime end = DateUtils.getWeekEndTime(now);
        // 3.查询总的统计数据
        // 3.1本周学习的总的小节数量
        Integer weekFinished = recordMapper.selectCount(new LambdaQueryWrapper<LearningRecord>()
                .eq(LearningRecord::getUserId, userId)
                .eq(LearningRecord::getFinished, true)
                .gt(LearningRecord::getFinishTime, begin)
                .lt(LearningRecord::getFinishTime, end)
        );
        result.setWeekFinished(weekFinished);
        // 3.2本周计划学习的小节数量
        Integer weekTotalPlan = getBaseMapper().queryTotalPlan(userId);
        result.setWeekTotalPlan(weekTotalPlan);
        // TODO 3.3本周学习积分

        // 4.查询分页数据
        // 4.1分页查询课表信息以及学习计划信息
        Page<LearningLesson> p = lambdaQuery()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getPlanStatus, PlanStatus.PLAN_RUNNING)
                .in(LearningLesson::getStatus, LessonStatus.NOT_BEGIN, LessonStatus.LEARNING)
                .page(query.toMpPage("latest_learn_time", false));
        List<LearningLesson> records = p.getRecords();
        if(CollUtils.isEmpty(records)){
            return result.pageInfo(PageDTO.empty(p));
        }
        // 4.2查询课表对应的课程信息
        Map<Long, CourseSimpleInfoDTO> cMap = queryCourseSimpleInfoList(records);
        // 4.3统计每一个课程本周学习的小节数量
        List<IdAndNumDTO> list = recordMapper.countLearnedSections(userId,begin,end);
        Map<Long, Integer> countMap = IdAndNumDTO.toMap(list);
        // 4.4封装数据VO
        List<LearningPlanVO> voList = new ArrayList<>(records.size());
        for (LearningLesson r : records) {
            // 拷贝基础属性
            LearningPlanVO vo = BeanUtils.copyBean(r, LearningPlanVO.class);
            // 填充课程详细信息
            CourseSimpleInfoDTO cInfo = cMap.get(r.getCourseId());
            if(cInfo != null){
                vo.setCourseName(cInfo.getName());
                vo.setSections(cInfo.getSectionNum());
            }
            // 每个课程本周的已学习小节数量
            vo.setWeekLearnedSections(countMap.getOrDefault(r.getId(),0));
            voList.add(vo);
        }

        return result.pageInfo(p.getTotal(),p.getPages(),voList);
    }


    private Wrapper<LearningLesson> buildUserIdAndCourseIdWrapper(Long userId, Long courseId) {
        LambdaQueryWrapper<LearningLesson> queryWrapper = new QueryWrapper<LearningLesson>()
                .lambda()
                .eq(LearningLesson::getUserId, userId)
                .eq(LearningLesson::getCourseId, courseId);
        return queryWrapper;
    }
}
