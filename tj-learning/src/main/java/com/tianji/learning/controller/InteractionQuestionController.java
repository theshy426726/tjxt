package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author theshy
 * @since 2026-11-05
 */
@RestController
@RequestMapping("/questions")
@RequiredArgsConstructor
@Api(tags = "互动问答的相关接口")
public class InteractionQuestionController {

    private final IInteractionQuestionService questionService;

    @PostMapping
    @ApiOperation("新增互动问题")
    public void saveQuestion(@Valid @RequestBody QuestionFormDTO questionDTO){
        questionService.saveQuestion(questionDTO);
    }


    @GetMapping("/page")
    @ApiOperation("分页查询互动问题")
    public PageDTO<QuestionVO> queryQuestionPage(QuestionPageQuery query){
        return questionService.queryQuestionPage(query);
    }


    @GetMapping("/{id}")
    @ApiOperation("根据id查询问题详情")
    public QuestionVO queryQuestionById(@PathVariable Long id){
        return questionService.queryQuestionById(id);
    }

    @PutMapping("/{id}")
    @ApiOperation("修改提问问题的标题、描述信息、是否匿名")
    public void updateQuestion(@PathVariable("id") Long id, QuestionFormDTO questionDTO){
        questionService.updateQuestion(id,questionDTO);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("根据id删除问题")
    public void deleteQuestion(@PathVariable("id") Long id){
        questionService.deleteQuestion(id);
    }
}
