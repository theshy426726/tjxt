package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author theshy
 * @since 2026-11-08
 */
//@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;

    @Override
    public void addLikeRecord(LikeRecordFormDTO recordDTO) {
        // 基于前端参数，判断是执行点赞还是取消点赞
        Boolean success = recordDTO.getLiked() ? like(recordDTO) : unlike(recordDTO);
        // 判断是否执行成功，如果失败，则直接结束
        if(!success){
            return;
        }
        // 如果执行成功，统计点赞总数
        Integer likeTimes = lambdaQuery().eq(LikedRecord::getBizId, recordDTO.getBizId()).count();
        // 发送MQ通知
        mqHelper.send(LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE,recordDTO.getBizType()),
                LikeTimesDTO.of(recordDTO.getBizId(),likeTimes));
    }

    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 查询点赞状态
        List<LikedRecord> list = lambdaQuery().in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        // 返回结果
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }

    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize) {

    }

    private Boolean unlike(LikeRecordFormDTO recordDTO) {
        return remove(new QueryWrapper<LikedRecord>().lambda()
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .eq(LikedRecord::getBizId, recordDTO.getBizId()));
    }

    private Boolean like(LikeRecordFormDTO recordDTO) {
        // 查询点赞记录
        Integer count = lambdaQuery()
                .eq(LikedRecord::getUserId, UserContext.getUser())
                .eq(LikedRecord::getBizId, recordDTO.getBizId())
                .count();
        // 判断点赞是否存在，如果存在，直接结束
        if(count > 0){
            return false;
        }
        // 如果不存在，直接新增
        LikedRecord record = BeanUtils.copyBean(recordDTO, LikedRecord.class);
        record.setUserId(UserContext.getUser());
        save(record);
        return true;
    }
}
