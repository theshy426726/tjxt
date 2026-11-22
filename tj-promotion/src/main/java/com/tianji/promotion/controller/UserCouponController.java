package com.tianji.promotion.controller;


import com.tianji.api.dto.promotion.CouponDiscountDTO;
import com.tianji.api.dto.promotion.OrderCouponDTO;
import com.tianji.api.dto.promotion.OrderCourseDTO;
import com.tianji.promotion.service.IDiscountService;
import com.tianji.promotion.service.IUserCouponService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 前端控制器
 * </p>
 *
 * @author theshy
 * @since 2026-11-14
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/user-coupons")
@Api(tags = "优惠券相关接口")
public class UserCouponController {
    private final IUserCouponService userCouponService;
    private final IDiscountService discountService;

    @PostMapping("/{couponId}/receive")
    @ApiOperation("领取优惠券接口")
    public void receiveCoupon(@PathVariable("couponId") Long couponId){
        userCouponService.receiveCoupon(couponId);
    }

    @PostMapping("/{code}/exchange")
    @ApiOperation("兑换码兑换优惠券接口")
    public void exchangeCoupon(@PathVariable("code") String code){
        userCouponService.exchangeCoupon(code);
    }

    @PostMapping("/available")
    @ApiOperation("查询我的优惠券的可用方案")
    public List<CouponDiscountDTO> findDiscountSolution(@RequestBody List<OrderCourseDTO> orderCourses){
        return discountService.findDiscountSolution(orderCourses);
    }

    @ApiOperation("根据券方案计算订单优惠明细")
    @PostMapping("/discount")
    public CouponDiscountDTO queryDiscountDetailByOrder(
            @RequestBody OrderCouponDTO orderCouponDTO){
        return discountService.queryDiscountDetailByOrder(orderCouponDTO);
    }

    @ApiOperation("核销指定优惠券")
    @PutMapping("/use")
    public void writeOffCoupon(@ApiParam("用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds){
        userCouponService.writeOffCoupon(userCouponIds);
    }

    @ApiOperation("退还指定优惠券")
    @PutMapping("/refund")
    public void refundCoupon(@ApiParam("用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds){
        userCouponService.refundCoupon(userCouponIds);
    }

    @ApiOperation("分页查询我的优惠券接口")
    @GetMapping("/rules")
    public List<String> queryDiscountRules(
            @ApiParam("用户优惠券id集合") @RequestParam("couponIds") List<Long> userCouponIds){
        return userCouponService.queryDiscountRules(userCouponIds);
    }
}
