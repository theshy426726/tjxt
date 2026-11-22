package com.tianji.promotion.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.service.ICouponService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueTaskHandler {

    private final ICouponService couponService;

    @XxlJob("beginCouponIssueJob")
    public void handleCouponIssueJob(){
        log.debug("正在执行定时发放优惠券的任务");
        // 查询所有符合条件的定时优惠券任务(条件为未发放，开始发放时间小于当前时间)
        List<Coupon> needIssueCoupons = couponService.lambdaQuery().eq(Coupon::getStatus, CouponStatus.UN_ISSUE)
                .le(Coupon::getIssueBeginTime, LocalDateTime.now())
                .list();
        // 判空
        if(CollUtils.isEmpty(needIssueCoupons)){
            return;
        }
        // 批量发放符合条件的优惠券
        couponService.issueCouponBatch(needIssueCoupons);
    }

    @XxlJob("endCouponIssueJob")
    public void handleCouponEndIssueJob(){
        log.debug("正在执行定时结束发放优惠券的任务");
        // 查询所有符合条件的定时任务(条件为进行中，结束发放时间小于当前时间)
        List<Coupon> needEndCoupons = couponService.lambdaQuery().eq(Coupon::getStatus, CouponStatus.ISSUING)
                .le(Coupon::getIssueEndTime, LocalDateTime.now())
                .list();

        if(CollUtils.isEmpty(needEndCoupons)){
            return;
        }

        // 批量结束发放符合条件的优惠券
        couponService.endCouponBatch(needEndCoupons);
    }
}
