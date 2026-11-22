package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianji.api.client.remark.RemarkClient;
import com.tianji.api.client.user.UserClient;
import com.tianji.api.dto.user.UserDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.domain.po.InteractionReply;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.enums.QuestionStatus;
import com.tianji.learning.mapper.InteractionReplyMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.tianji.learning.service.IInteractionReplyService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_CREATE_TIME;
import static com.tianji.common.constants.Constant.DATA_FIELD_NAME_LIKED_TIME;

/**
 * <p>
 * 互动问题的回答或评论 服务实现类
 * </p>
 *
 * @author theshy
 * @since 2026-11-05
 */
@Service
@RequiredArgsConstructor
public class InteractionReplyServiceImpl extends ServiceImpl<InteractionReplyMapper, InteractionReply> implements IInteractionReplyService {

    private final IInteractionQuestionService questionService;
    
    private final UserClient userClient;

    private final RemarkClient remarkClient;

    private final RabbitMqHelper mqHelper;


    @Override
    public void saveReply(ReplyDTO replyDTO) {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 新增回答
        InteractionReply reply = BeanUtils.copyBean(replyDTO, InteractionReply.class);
        reply.setUserId(userId);
        save(reply);

        // 判断是否是回答
        boolean isAnswer = replyDTO.getAnswerId() == null;
        // 如果是评论，则需要更新上级回答下的评论数量
        if(!isAnswer){
            lambdaUpdate()
                    .setSql("reply_times = reply_times + 1")
                    .eq(InteractionReply::getId,replyDTO.getAnswerId())
                    .update();
        }

        // 如果是回答，修改问题表中最近一次回答id,回答数量，问题状态修改为未查看
        questionService.lambdaUpdate()
                .set(isAnswer, InteractionQuestion::getLatestAnswerId,reply.getAnswerId())
                .setSql(isAnswer,"answer_times = answer_times + 1")
                .set(replyDTO.getIsStudent(),InteractionQuestion::getStatus, QuestionStatus.UN_CHECK.getValue())
                .update();
        // 发送消息到MQ，累加积分,只有学生才能得到积分
        if(replyDTO.getIsStudent()){
            mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,MqConstants.Key.WRITE_REPLY,5);
        }
    }

    @Override
    public PageDTO<ReplyVO> queryReplyPage(ReplyPageQuery query, boolean forAdmin) {
        // 问题id和回答id至少要有一个，先进行判断
        Long questionId = query.getQuestionId();
        Long answerId = query.getAnswerId();
        if(questionId == null && answerId == null){
            throw new BadRequestException("问题或回答id不能都为空");
        }

        // 标记当前是查询问题下的回答
        boolean isQueryAnswer = questionId != null;
        // 分页查询reply
        Page<InteractionReply> page = lambdaQuery()
                .eq(isQueryAnswer, InteractionReply::getQuestionId, questionId)  // 如果是查回答，根据问题id查询
                .eq(InteractionReply::getAnswerId, isQueryAnswer ? 0L : answerId)  // 如果是查评论，根据回答id查询
                .eq(!forAdmin, InteractionReply::getHidden, false)  // 如果是用户端，需要过滤被hidden的回答或评论
                .page(query.toMpPage(
                        new OrderItem(DATA_FIELD_NAME_LIKED_TIME, false),
                        new OrderItem(DATA_FIELD_NAME_CREATE_TIME, true)));
        List<InteractionReply> records = page.getRecords();
        if(CollUtils.isEmpty(records)){
            return PageDTO.empty(page);
        }

        // 数据处理,需要查询提问者信息、回复者信息
        Set<Long> userIds = new HashSet<>();
        Set<Long> answerIds = new HashSet<>();
        Set<Long> targetReplyIds = new HashSet<>();
        // 获取提问者id，回复的目标id
        for (InteractionReply r : records) {
            if(!r.getAnonymity() || forAdmin){
                // 非匿名
                userIds.add(r.getUserId());
            }
            targetReplyIds.add(r.getTargetReplyId());
            answerIds.add(r.getId());
        }
        // 将回复者id也存入userIds
        targetReplyIds.remove(0L);
        targetReplyIds.remove(null);
        if(targetReplyIds.size() > 0){
            List<InteractionReply> targetReplies = listByIds(targetReplyIds);
            Set<Long> targetUserIds = targetReplies.stream().filter(Predicate.not(InteractionReply::getAnonymity).or(r -> forAdmin))  //排除匿名
                    .map(InteractionReply::getUserId)
                    .collect(Collectors.toSet());
            userIds.addAll(targetUserIds);
        }
        // 查询用户,并转换成map格式
        Map<Long, UserDTO> userMap = new HashMap<>(userIds.size());
        if(userIds.size() > 0){
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 查看用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(answerIds);
        // 处理VO
        List<ReplyVO> list = new ArrayList<>(records.size());
        for (InteractionReply r : records) {
            // 拷贝基础属性
            ReplyVO v = BeanUtils.copyBean(r, ReplyVO.class);
            list.add(v);
            // 回复人信息
            if(!r.getAnonymity() || forAdmin){
                UserDTO userDTO = userMap.get(r.getUserId());
                if(userDTO != null){
                    v.setUserIcon(userDTO.getIcon());
                    v.setUserName(userDTO.getUsername());
                    v.setUserType(userDTO.getType());
                }
            }
            // 如果存在评论的目标，则需要设置目标的用户信息
            if(r.getTargetReplyId() != null){
                UserDTO targetUser = userMap.get(r.getTargetUserId());
                if(targetUser != null){
                    v.setTargetUserName(targetUser.getName());
                }
            }
            // 点赞状态
            v.setLiked(bizLiked.contains(r.getId()));
        }

        return new PageDTO<>(page.getTotal(),page.getPages(),list);
    }

    @Override
    public void hiddenReply(Long id, boolean hidden) {
        // 查询
        InteractionReply old = getById(id);
        if(old == null){
            return;
        }
        // 隐藏回答
        InteractionReply reply = new InteractionReply();
        reply.setId(id);
        reply.setHidden(hidden);
        updateById(reply);
        // 先判断是否是回答，如果是回答，还要隐藏回答下的评论
        if(old.getAnswerId() != null && old.getAnswerId() != 0){
            return;
        }
        lambdaUpdate()
                .set(InteractionReply::getHidden,hidden)
                .eq(InteractionReply::getAnswerId,id)
                .update();
    }

    @Override
    public ReplyVO queryReplyById(Long id) {
        // 根据id查询
        InteractionReply r = getById(id);
        // 创建要查询的用户集合
        Set<Long> userIds = new HashSet<>();
        // 获取用户id
        userIds.add(r.getUserId());
        // 查询评论目标，如果目标不是匿名的，还要查询出目标回复的用户id
        if(r.getTargetReplyId() != null && r.getTargetReplyId() != 0){
            InteractionReply target = getById(r.getTargetReplyId());
            if(!target.getAnonymity()){
                userIds.add(target.getUserId());
            }
        }
        // 根据userIds查询用户信息
        Map<Long,UserDTO> userMap = new HashMap<>(userIds.size());
        if(userIds.size() > 0){
            List<UserDTO> users = userClient.queryUserByIds(userIds);
            userMap = users.stream().collect(Collectors.toMap(UserDTO::getId, u -> u));
        }
        // 查询用户点赞状态
        Set<Long> bizLiked = remarkClient.isBizLiked(CollUtils.singletonList(id));
        // 处理VO
        // 属性拷贝
        ReplyVO v = BeanUtils.copyBean(r, ReplyVO.class);
        // 设置回复人信息
        UserDTO userDTO = userMap.get(r.getUserId());
        if(userDTO != null){
            v.setUserIcon(userDTO.getIcon());
            v.setUserName(userDTO.getName());
            v.setUserType(userDTO.getType());
        }
        // 设置目标用户
        UserDTO targetUser = userMap.get(r.getTargetUserId());
        if(targetUser != null){
            v.setTargetUserName(targetUser.getName());
        }

        // 点赞状态
        v.setLiked(bizLiked.contains(id));
        return v;
    }

}
