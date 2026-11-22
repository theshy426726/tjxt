package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author theshy
 * @since 2026-11-13
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    private final ICouponScopeService scopeService;
    private final CategoryCache categoryCache;
    private final IExchangeCodeService codeService;
    private final StringRedisTemplate redisTemplate;
    private final IUserCouponService userCouponService;

    @Override
    @Transactional
    public void saveCoupon(CouponFormDTO dto) {
        // 保存优惠券
        Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
        save(coupon);

        if(!dto.getSpecific()){
            return;
        }
        Long couponId = coupon.getId();
        // 保存存储范围
        List<Long> scopes = dto.getScopes();
        if(CollUtils.isEmpty(scopes)){
            throw new BadRequestException("限定范围不能为空");
        }
        List<CouponScope> list = scopes.stream()
                .map(bizId -> new CouponScope().setBizId(bizId).setCouponId(couponId))
                .collect(Collectors.toList());
        scopeService.saveBatch(list);
    }

    @Override
    public PageDTO<CouponPageVO> queryCouponByPage(CouponQuery query) {
        Integer type = query.getType();
        String name = query.getName();
        Integer status = query.getStatus();
        // 分页查询
        Page<Coupon> page = lambdaQuery().eq(type != null, Coupon::getDiscountType, type)
                .eq(status != null, Coupon::getStatus, status)
                .like(StringUtils.isNotBlank(name), Coupon::getName, name)
                .page(query.toMpPageDefaultSortByCreateTimeDesc());
        // 处理成VO
        List<Coupon> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }
        List<CouponPageVO> list = BeanUtils.copyList(records, CouponPageVO.class);
        // 返回
        return PageDTO.of(page,list);
    }

    @Override
    public CouponDetailVO queryCouponById(Long id) {
        // 查询PO
        Coupon coupon = getById(id);
        // 转换成VO
        CouponDetailVO vo = BeanUtils.copyBean(coupon, CouponDetailVO.class);
        // 如果数据不存在，或者没有限定作用范围
        if(vo == null || !coupon.getSpecific()){
            return vo;
        }
        // PO转VO，还差优惠券使用范围，查询限定范围
        List<CouponScope> scopes = scopeService.lambdaQuery().eq(CouponScope::getCouponId, id).list();
        if(CollUtils.isEmpty(scopes)){
            return vo;
        }
        List<CouponScopeVO> scopeVOs = scopes.stream()
                .map(CouponScope::getBizId)
                .map(cataId -> new CouponScopeVO(cataId, categoryCache.getNameByLv3Id(cataId)))
                .collect(Collectors.toList());
        vo.setScopes(scopeVOs);
        return vo;
    }

    @Override
    public void deleteById(Long id) {
        // 删除前先保证优惠券状态是待发放状态
        Coupon coupon = getById(id);
        if(coupon != null || coupon.getStatus() != CouponStatus.DRAFT){
            throw new BadRequestException("优惠券不存在或者优惠券正在使用中");
        }
        // 删除优惠券
        boolean success = remove(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getId, id)
                .eq(Coupon::getStatus, CouponStatus.DRAFT)
        );
        if(!success){
            throw new BadRequestException("优惠券不存在或者优惠券正在使用中");
        }
        // 删除CouponScope表中优惠券相关的数据
        if(!coupon.getSpecific()){
            return;
        }
        scopeService.remove(new LambdaQueryWrapper<CouponScope>().eq(CouponScope::getCouponId,id));
    }

    @Override
    public void updateCouponById(Long id, CouponFormDTO dto) {
        Coupon coupon = getById(id);
        if(coupon == null){
            throw new BadRequestException("优惠券不存在");
        }
        // 只有待发放状态下的优惠券才能修改
        if(coupon.getStatus() != CouponStatus.DRAFT){
            throw new BadRequestException("只有待发放状态的优惠券才能修改");
        }
        // 将DTO转换成PO
        Coupon updateCoupon = BeanUtils.copyBean(dto, Coupon.class);
        // 更新优惠券
        updateById(updateCoupon);

        // 单独针对优惠券的限定作用范围
        // 先删除id对应的作用范围scope表
        if(coupon.getSpecific()){
            scopeService.remove(new LambdaQueryWrapper<CouponScope>().eq(CouponScope::getCouponId,id));
        }

        if(dto.getSpecific()){
            List<Long> scopes = dto.getScopes();
            if(CollUtils.isEmpty(scopes)){
                throw new BadRequestException("更新限定范围：不能为空");
            }
            // 再新增dto的限定作用范围到scope表
            List<CouponScope> list = scopes.stream()
                    .map(aLong -> new CouponScope().setCouponId(id).setBizId(aLong).setType(1))
                    .collect(Collectors.toList());
            scopeService.saveBatch(list);
        }
    }

    @Transactional
    @Override
    public void beginIssue(CouponIssueFormDTO dto) {
        // 1.查询优惠券
        Coupon coupon = getById(dto.getId());
        if (coupon == null) {
            throw new BadRequestException("优惠券不存在");
        }
        // 2.判断优惠券的状态是否是暂停还是待发放
        if(coupon.getStatus() != CouponStatus.DRAFT && coupon.getStatus() != CouponStatus.PAUSE){
            throw new BizIllegalException("优惠券状态错误");
        }
        // 3.判断是否是立即发放(没有发放开始时间，就是立刻发放)
        LocalDateTime issueBeginTime = dto.getIssueBeginTime();
        LocalDateTime now = LocalDateTime.now();
        boolean isBegin = issueBeginTime == null || !issueBeginTime.isAfter(now);
        // 4.更新优惠券
        // 4.1拷贝属性到PO
        Coupon c = BeanUtils.copyBean(dto, Coupon.class);
        // 4.2更新状态
        if(isBegin){
            c.setStatus(CouponStatus.ISSUING);
            c.setIssueBeginTime(now);
        }else{
            c.setStatus(CouponStatus.UN_ISSUE);
        }
        // 4.3写入数据库
        updateById(c);

        // 如果是立刻发放，添加缓存
        if(isBegin){
            coupon.setIssueBeginTime(c.getIssueBeginTime());
            coupon.setIssueEndTime(c.getIssueEndTime());
            cacheCouponInfo(coupon);
        }

        // 判断是否需要生成兑换码（优惠券发放类型是兑换码发放类型，优惠券的状态是待发放）
        if(coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT){
            coupon.setIssueEndTime(c.getIssueEndTime());
            codeService.asyncGenerateCode(coupon);
        }
    }

    private void cacheCouponInfo(Coupon coupon) {
        // 组织数据
        Map<String, String> map = new HashMap<>(4);
        map.put("issueBeginTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueBeginTime())));
        map.put("issueEndTime", String.valueOf(DateUtils.toEpochMilli(coupon.getIssueEndTime())));
        map.put("totalNum", String.valueOf(coupon.getTotalNum()));
        map.put("userLimit", String.valueOf(coupon.getUserLimit()));
        // 写入redis
        redisTemplate.opsForHash().putAll(PromotionConstants.COUPON_CACHE_KEY_PREFIX + coupon.getId(),map);
    }

    @Override
    @Transactional
    public void pauseIssue(Long id) {
        Coupon coupon = getById(id);
        if(coupon == null){
            throw new BadRequestException("优惠券不存在");
        }

        // 当前优惠券状态必须是进行中
        if(coupon.getStatus() != CouponStatus.ISSUING){
            return;
        }

        // 更新状态
        boolean success = lambdaUpdate().set(Coupon::getStatus, CouponStatus.PAUSE)
                .eq(Coupon::getId, id)
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .update();
        if(!success){
            log.error("重复暂停优惠券");
        }

        // 删除缓存
        redisTemplate.delete(PromotionConstants.COUPON_CACHE_KEY_PREFIX + id);
    }

    @Override
    public void issueCouponBatch(List<Coupon> needIssueCoupons) {
        // 将所有待发放的优惠券的状态改为发放中
        for (Coupon coupon : needIssueCoupons) {
            coupon.setStatus(CouponStatus.ISSUING);
        }
        // 批量更新优惠券状态
        updateBatchById(needIssueCoupons);
    }

    @Override
    public void endCouponBatch(List<Coupon> needEndCoupons) {
        // 将所有待发放的优惠券的状态改为已结束
        for (Coupon coupon : needEndCoupons) {
            coupon.setStatus(CouponStatus.FINISHED);
        }

        // 批量更新优惠券状态
        updateBatchById(needEndCoupons);
    }

    @Override
    public List<CouponVO> queryIssuingCoupons() {
        // 1.查询发放中的优惠券列表
        List<Coupon> coupons = lambdaQuery()
                .eq(Coupon::getStatus, CouponStatus.ISSUING)
                .eq(Coupon::getObtainWay, ObtainType.PUBLIC)
                .list();
        if(CollUtils.isEmpty(coupons)){
            return CollUtils.emptyList();
        }

        // 2.统计当前用户已经领取的优惠券的信息
        List<Long> couponIds = coupons.stream().map(Coupon::getId).collect(Collectors.toList());
        // 2.1查询当前用户已经领取的优惠券的数据
        List<UserCoupon> userCoupons = userCouponService.lambdaQuery()
                .eq(UserCoupon::getUserId, UserContext.getUser())
                .in(UserCoupon::getCouponId, couponIds)
                .list();
        // 2.2统计当前用户已经领取优惠券的数量
        Map<Long, Long> issuedMap = userCoupons.stream()
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
        // 2.3统计当前用户已经领取优惠券并未使用的数量
        Map<Long, Long> unusedMap = userCoupons.stream().filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
                .collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));


        // 3.封装VO结果
        List<CouponVO> list = new ArrayList<>(coupons.size());
        for (Coupon coupon : coupons) {
            // 属性拷贝(缺少是否可以领域、是否可以使用两个字段)
            CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
            list.add(vo);
            // 是否可以领取: 已经被领取的数量 < 优惠券总数量  &&  当前用户已经领取的数量 < 每人限领数量
            vo.setAvailable(coupon.getIssueNum() < coupon.getTotalNum() && issuedMap.getOrDefault(coupon.getId(),0L) < coupon.getUserLimit());
            // 是否可以使用: 当前用户存在已经领取但未使用的优惠券
            vo.setReceived(unusedMap.getOrDefault(coupon.getId(),0L) > 0);
        }

        return list;
    }
}
