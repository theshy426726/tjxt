package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author theshy
 * @since 2026-10-29
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {

    Integer queryTotalPlan(Long userId);
}
