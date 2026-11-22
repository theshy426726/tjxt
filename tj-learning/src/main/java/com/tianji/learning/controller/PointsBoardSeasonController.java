package com.tianji.learning.controller;


import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author theshy
 * @since 2026-11-09
 */
@RestController
@RequestMapping("/boards/seasons")
@RequiredArgsConstructor
public class PointsBoardSeasonController {

    private final IPointsBoardSeasonService seasonService;

    @GetMapping("/list")
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons(){
        // 获取时间
        LocalDateTime now = LocalDateTime.now();
        // 查询赛季列表(开始时间小于等于当前时间)
        List<PointsBoardSeason> list = seasonService.lambdaQuery()
                .le(PointsBoardSeason::getBeginTime, now).list();
        if(CollUtils.isEmpty(list)){
            return CollUtils.emptyList();
        }
        return BeanUtils.copyToList(list, PointsBoardSeasonVO.class);
    }
}
