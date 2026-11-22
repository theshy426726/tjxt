package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author theshy
 * @since 2026-10-29
 */
@RestController
@RequestMapping("/lessons")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表，排序字段: latest_learn_time, create_time")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        return lessonService.queryMyLessons(query);
    }


    @GetMapping("/now")
    @ApiOperation("查询我正在学习的课程")
    public LearningLessonVO queryMyCurrentLesson(){
        return lessonService.queryMyCurrentLesson();
    }


    @DeleteMapping("/{courseId}")
    @ApiOperation("从课表中删除课程")
    public void deleteCourseFromLesson(@PathVariable Long courseId){
        lessonService.deleteCourseFromLesson(null,courseId);
    }


    /**
     * 校验当前用户是否可以学习当前课程，如果报名了，返回课程id，否则返回null
     * @param courseId
     * @return
     */
    @GetMapping("/{courseId}/valid")
    @ApiOperation("校验当前用户是否可以学习当前课程")
    public Long isLessonValid(@PathVariable Long courseId){
        return lessonService.isLessonValid(courseId);
    }


    @GetMapping("/{courseId}")
    @ApiOperation("查询用户课表中指定课程状态")
    public LearningLessonVO queryLessonByCourseId(@PathVariable Long courseId){
        return lessonService.queryLessonByCourseId(courseId);
    }


    @GetMapping("/{courseId}/count")
    @ApiOperation("统计课程学习人数")
    public Integer countLearningLessonByCourse(@PathVariable("courseId") Long courseId){
        return lessonService.countLearningLessonByCourse(courseId);
    }


    @PostMapping("/plans")
    @ApiOperation("创建学习计划")
    public void createLearningPlans(@Valid @RequestBody LearningPlanDTO planDTO){
        lessonService.createLearningPlan(planDTO.getCourseId(),planDTO.getFreq());
    }


    @GetMapping("/plans")
    @ApiOperation("查询我的学习计划")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return lessonService.queryMyPlans(query);
    }
}
