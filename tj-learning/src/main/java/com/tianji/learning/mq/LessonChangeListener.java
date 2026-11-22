package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LessonChangeListener {

    private final ILearningLessonService lessonService;


    /**
     * 监听订单支付或课程报名的消息
     * @param order 订单信息
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_PAY_KEY
    ))
    public void listenLessonPay(OrderBasicDTO order){
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())){
            return;
        }
        // 添加课程
        lessonService.addUserLessons(order.getUserId(),order.getCourseIds());
    }


    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.refund.queue",durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO order){
        if(order == null || order.getUserId() == null || CollUtils.isEmpty(order.getCourseIds())){
            return;
        }
        // 删除课程
        lessonService.deleteCourseFromLesson(order.getUserId(),order.getCourseIds().get(0));
    }
}
