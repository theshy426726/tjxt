package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author theshy
 * @since 2026-11-05
 */
@RestController
@RequestMapping("/admin/replies")
@RequiredArgsConstructor
@Api(tags = "问题管理相关接口")
public class InteractionReplyAdminController {

    private final IInteractionReplyService replyService;

    @GetMapping("/page")
    @ApiOperation("管理端分页查询回答或者评论")
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery pageQuery){
        return replyService.queryReplyPage(pageQuery,true);
    }


    @PutMapping("/{id}/hidden/{hidden}")
    @GetMapping("隐藏或者显示评论")
    public void hiddenReply(@PathVariable("id") Long id,@PathVariable("hidden") boolean hidden){
        replyService.hiddenReply(id,hidden);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询回答或者评论")
    public ReplyVO queryReplyById(@PathVariable("id") Long id){
        return replyService.queryReplyById(id);
    }

}
