package com.tianji.learning.service.impl;

import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BooleanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl implements ISignRecordService {

    private final StringRedisTemplate redisTemplate;

    private final RabbitMqHelper mqHelper;

    @Override
    public SignResultVO addSignRecords() {
        // 签到
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 获取日期
        LocalDate now = LocalDate.now();
        // 拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        // 计算offset
        int offset = now.getDayOfMonth() - 1;
        // 保存签到信息
        Boolean exits = redisTemplate.opsForValue().setBit(key, offset, true);
        if(BooleanUtils.isTrue(exits)){
            throw new BizIllegalException("不允许重复签到");
        }

        // 计算连续签到的天数
        int signDays = countSignDays(key,now.getDayOfMonth());

        // 计算签到得分
        int rewardPoints = 0;
        switch (signDays){
            case 7:
                rewardPoints = 10;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }

        // 保存积分明细记录
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,MqConstants.Key.SIGN_IN, SignInMessage.of(userId,rewardPoints + 1));

        // 封装返回
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signDays);
        vo.setRewardPoints(rewardPoints);
        return vo;
    }

    @Override
    public Byte[] querySignRecords() {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 获取日期
        LocalDate now = LocalDate.now();
        int dayOfMonth = now.getDayOfMonth();
        // 拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);

        // 读取
        List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(
                BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)
        ).valueAt(0));
        if (CollUtils.isEmpty(result)) {
            return new Byte[0];
        }
        int num = result.get(0).intValue();

        Byte[] arr = new Byte[dayOfMonth];
        int pos = dayOfMonth - 1;
        while (pos >= 0){
            arr[pos--] = (byte) (num & 1);
            // 数字右移一位，抛弃最后一个bit
            num >>>= 1;
        }

        return arr;
    }

    private int countSignDays(String key, int len) {
        // 获取本月从第一天到今天为止所有的签到记录
        List<Long> result = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(
                BitFieldSubCommands.BitFieldType.unsigned(len)
        ).valueAt(0));
        if(CollUtils.isEmpty(result)){
            return 0;
        }
        int num = result.get(0).intValue();

        // 定义记录器
        int count = 0;
        // 循环，与1做与运算，得到最后一个bit，如果为0则终止，如果为1则继续循环
        while ((num & 1) == 1){
            // 计数器+1
            count++;
            // 把签到记录数字右移一位，最后一位被舍弃，继续循环
            num >>>= 1;
        }
        return count;
    }
}
