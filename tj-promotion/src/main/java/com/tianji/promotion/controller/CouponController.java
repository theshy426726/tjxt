package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.service.ICouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.units.qual.A;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 优惠券的规则信息 前端控制器
 * </p>
 *
 * @author theshy
 * @since 2026-11-13
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/coupons")
@Api(tags = "优惠券相关接口")
public class CouponController {

    private final ICouponService couponService;

    @PostMapping
    @ApiOperation("新增优惠券")
    public void saveCoupon(@RequestBody @Valid CouponFormDTO dto){
        couponService.saveCoupon(dto);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询优惠券")
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query){
        return couponService.queryCouponByPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询优惠券详情")
    public CouponDetailVO queryCouponById(@PathVariable("id") Long id){
        return couponService.queryCouponById(id);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("删除优惠券")
    public void deleteById(@PathVariable("id") Long id) {
        couponService.deleteById(id);
    }

    @PutMapping("/{id}")
    @ApiOperation("根据id修改优惠券")
    public void updateCouponById(@PathVariable("id") Long id,@RequestBody CouponFormDTO dto){
        couponService.updateCouponById(id,dto);
    }

    @PutMapping("/{id}/issue")
    @ApiOperation("发放优惠券")
    public void beginIssue(@RequestBody @Valid CouponIssueFormDTO dto){
        couponService.beginIssue(dto);
    }

    @ApiOperation("暂停发放优惠券")
    @PutMapping("/{id}/pause")
    public void pauseIssue(@PathVariable("id") Long id) {
        couponService.pauseIssue(id);
    }

    @GetMapping("/list")
    @ApiOperation("查询发放中的优惠券列表")
    public List<CouponVO> queryIssuingCoupons(){
        return couponService.queryIssuingCoupons();
    }
}
