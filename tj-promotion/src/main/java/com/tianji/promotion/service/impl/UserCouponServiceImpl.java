package com.tianji.promotion.service.impl;

import cn.hutool.core.bean.copier.CopyOptions;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.autoconfigure.redisson.annotations.Lock;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.UserCouponDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.utils.CodeUtil;
import com.tianji.promotion.utils.MyLock;
import com.tianji.promotion.utils.RedisLock;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.tianji.promotion.constants.PromotionConstants.USER_COUPON_CACHE_KEY_PREFIX;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author theshy
 * @since 2026-11-14
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {

    private final CouponMapper couponMapper;
    private final IExchangeCodeService codeService;
    private final StringRedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;

    @Override
    @Lock(name = "lock:coupon:#{couponId}")
    public void receiveCoupon(Long couponId) {
        // 查询优惠券
        Coupon coupon = queryCouponByCache(couponId);
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 校验发放时间
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())){
            throw new BadRequestException("优惠券发放已经结束或尚未开始");
        }
        // 校验库存
        if (coupon.getTotalNum() <= 0) {
            throw new BadRequestException("优惠券库存不足");
        }
        Long userId = UserContext.getUser();
        // 校验每人限领数量
        //   redis中查询领取数量
        String key = USER_COUPON_CACHE_KEY_PREFIX + couponId;
        Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
        //   校验限领数量
        if (count > coupon.getUserLimit()){
            throw new BadRequestException("超出领取数量");
        }

        // 扣减优惠券库存
        redisTemplate.opsForHash().increment(PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId,"totalNum",-1);

        // 发送MQ消息
        UserCouponDTO uc = new UserCouponDTO();
        uc.setUserId(userId);
        uc.setCouponId(couponId);
        mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE,MqConstants.Key.COUPON_RECEIVE,uc);

    }

    private Coupon queryCouponByCache(Long couponId) {
        // 准备key
        String key = PromotionConstants.COUPON_CACHE_KEY_PREFIX + couponId;
        // 查询redis
        Map<Object, Object> objMap = redisTemplate.opsForHash().entries(key);
        if(objMap.isEmpty()){
            return null;
        }
        // 将map转换成Coupon
        return BeanUtils.mapToBean(objMap,Coupon.class,false, CopyOptions.create());
    }

    @Transactional
    @Override
    public void checkAndCreateUserCoupon(UserCouponDTO uc) {
        // 查优惠券
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if(coupon == null){
            throw new BizIllegalException("优惠券不存在");
        }

        // 更新优惠券的已发放数量+1
        int r = couponMapper.incrIssueNum(coupon.getId());
        if(r == 0){
            throw new BizIllegalException("优惠券库存不足");
        }

        // 新增一个用户券
        saveUserCoupon(uc.getUserId(),coupon);
    }

    @Override
    @Transactional
    public void exchangeCoupon(String code) {
        // 1.校验并解析兑换码
        long serialNum = CodeUtil.parseCode(code);
        // 2.校验是否已经兑换 bitmap
        boolean exchanged = codeService.updateExchangeMark(serialNum,true);
        if(exchanged){
            throw new BizIllegalException("兑换码已经兑换过了");
        }
        try {
            // 3.查询兑换码对应的couponId
            Long couponId = codeService.exchangeTargetId(serialNum);
            if (couponId == null) {
                throw new BizIllegalException("兑换码不存在");
            }
            Coupon coupon = queryCouponByCache(couponId);
            // 4.是否过期
            LocalDateTime now = LocalDateTime.now();
            if(now.isAfter(coupon.getIssueEndTime()) || now.isBefore(coupon.getIssueBeginTime())){
                throw new BizIllegalException("优惠券活动未开始或已经结束");
            }

            // 5.校验每人限领数量
            Long userId = UserContext.getUser();
            // 5.1查询领取数量
            String key = USER_COUPON_CACHE_KEY_PREFIX + couponId;
            Long count = redisTemplate.opsForHash().increment(key, userId.toString(), 1);
            // 5.2校验限领数量
            if(count > coupon.getUserLimit()){
                throw new BadRequestException("超出领取数量");
            }

            // 6.发送MQ消息通知
            UserCouponDTO uc = new UserCouponDTO();
            uc.setUserId(userId);
            uc.setCouponId(couponId);
            uc.setSerialNum((int) serialNum);
            mqHelper.send(MqConstants.Exchange.PROMOTION_EXCHANGE, MqConstants.Key.COUPON_RECEIVE,uc);

        } catch (Exception e) {
            // 重置兑换的标记
            codeService.updateExchangeMark(serialNum,false);
            throw e;
        }
    }

    private void saveUserCoupon( Long userId, Coupon coupon) {
        // 基本信息
        UserCoupon uc = new UserCoupon();
        uc.setUserId(userId);
        uc.setCouponId(coupon.getId());
        // 有效期信息
        LocalDateTime termBeginTime = coupon.getTermBeginTime();
        LocalDateTime termEndTime = coupon.getTermEndTime();
        if(termBeginTime == null){
            termBeginTime = LocalDateTime.now();
            termEndTime = termBeginTime.plusDays(coupon.getTermDays());
        }
        uc.setTermBeginTime(termBeginTime);
        uc.setTermEndTime(termEndTime);
        save(uc);
    }
}
