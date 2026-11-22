package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author theshy
 * @since 2026-10-31
 */
@RestController
@RequestMapping("/learning-records")
@RequiredArgsConstructor
@Api(tags = "学习记录相关接口")
public class LearningRecordController {

    private final ILearningRecordService recordService;

    @GetMapping("/course/{courseId}")
    @ApiOperation("查询当前用户指定课程的学习记录")
    public LearningLessonDTO queryLearningRecordByCourse(@PathVariable("courseId") Long courseId){
        return recordService.queryLearningRecordByCourse(courseId);
    }

    @PostMapping
    @ApiOperation("提交学习记录")
    public void addLearningRecord(@RequestBody LearningRecordFormDTO formDTO){
        recordService.addLearningRecord(formDTO);
    }
}
