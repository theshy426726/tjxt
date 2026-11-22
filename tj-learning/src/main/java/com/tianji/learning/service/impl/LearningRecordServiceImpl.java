package com.tianji.learning.service.impl;

import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.api.dto.leanring.LearningRecordFormDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.mq.message.SignInMessage;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.learning.utils.LearningRecordDelayTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author theshy
 * @since 2026-10-31
 */
@Service
@RequiredArgsConstructor
public class LearningRecordServiceImpl extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;

    private final CourseClient courseClient;

    private final LearningRecordDelayTaskHandler taskHandler;

    private final RabbitMqHelper mqHelper;

    @Override
    public LearningLessonDTO queryLearningRecordByCourse(Long courseId) {
        // 获取登陆用户
        Long userId = UserContext.getUser();
        // 查询课表
        LearningLesson lesson = lessonService.queryByUserIdAndCourseId(userId,courseId);
        if(lesson == null){
            return null;
        }
        // 查询学习记录
        List<LearningRecord> records = lambdaQuery().eq(LearningRecord::getLessonId, lesson.getId()).list();

        // 封装结果
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setLatestSectionId(lesson.getLatestSectionId());
        dto.setRecords(BeanUtils.copyList(records, LearningRecordDTO.class));

        return dto;
    }


    @Override
    @Transactional
    public void addLearningRecord(LearningRecordFormDTO recordDTO) {
        // 1.获取登陆用户
        Long userId = UserContext.getUser();

        // 2.处理学习记录
        Boolean finished = false;
        if (recordDTO.getSectionType() == SectionType.VIDEO.getValue()) {
            // 2.1处理视频
            finished = handleVideoRecord(userId,recordDTO);
        }else{
            // 2.2处理考试
            finished = handleExamRecord(userId,recordDTO);
        }

        // 没有新学完的小节，无需更新课表中的学习进度
        if(!finished){
            return;
        }
        // 3.处理课表数据
        handleLearningLessonChanges(recordDTO);

        // 发送消息到MQ，保存积分
        mqHelper.send(MqConstants.Exchange.LEARNING_EXCHANGE,MqConstants.Key.LEARN_SECTION, SignInMessage.of(userId,10));
    }


    private void handleLearningLessonChanges(LearningRecordFormDTO recordDTO) {
        // 查询课表：为了得到LearningLesson中的learnedSections
        LearningLesson lesson = lessonService.getById(recordDTO.getLessonId());
        if(lesson == null){
            throw new BizIllegalException("课程不存在，无法更新数据");
        }

        // 判断是否有新的完成小节
        boolean allLearned = false;

        // 如果有，查询课程：为了得到Course中的sectionNum
        CourseFullInfoDTO cInfo = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
        if(cInfo == null){
            throw new BizIllegalException("课程不存在，无法更新数据");
        }
        // 判断课程是否全部学完
        allLearned = lesson.getLearnedSections() + 1 >= cInfo.getSectionNum();


        // 更新课表
        lessonService.lambdaUpdate()
                .set(allLearned, LearningLesson::getStatus, LessonStatus.FINISHED.getValue())
                .setSql( "learned_sections = learned_sections + 1")
                .set(lesson.getLearnedSections() == 0, LearningLesson::getStatus, LessonStatus.LEARNING.getValue())
                .eq(LearningLesson::getId, lesson.getId())
                .update();
    }

    private Boolean handleVideoRecord(Long userId, LearningRecordFormDTO recordDTO) {
        // 查询旧的学习记录
        LearningRecord old = queryOldRecord(recordDTO.getLessonId(),recordDTO.getSectionId());
        // 判断是否存在
        if(old == null){
            // 如果不存在，则新增
            // 将DTO转换为PO
            LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
            // 填充数据
            record.setUserId(userId);
            // 保存到数据库
            boolean success = save(record);
            if(!success){
                throw new DbException("新增学习记录失败");
            }

            return false;
        }
        // 如果存在，则更新
        // 判断是否是第一次完成
        boolean finished = recordDTO.getMoment() * 2 >= recordDTO.getDuration() && !old.getFinished();
        // 不是第一次学完
        if(!finished){
            LearningRecord record = new LearningRecord();
            record.setLessonId(recordDTO.getLessonId());
            record.setSectionId(recordDTO.getSectionId());
            record.setMoment(recordDTO.getMoment());
            record.setId(old.getId());
            record.setFinished(old.getFinished());
            taskHandler.addLearningRecordTask(record);
            return false;
        }

        // 是第一次学完
        // 更新数据
        boolean success = lambdaUpdate()
                .set(LearningRecord::getMoment, recordDTO.getMoment())
                .set(LearningRecord::getFinished, true)
                .set(LearningRecord::getFinishTime, recordDTO.getCommitTime())
                .eq(LearningRecord::getId, old.getId())
                .update();
        if(!success){
            throw new DbException("更新学习记录失败");
        }
        // 清理缓存
        taskHandler.cleanRecordCache(recordDTO.getLessonId(),recordDTO.getSectionId());

        return true;
    }

    private LearningRecord queryOldRecord(Long lessonId, Long sectionId) {
        // 查询缓存
        LearningRecord record = taskHandler.readRecordCache(lessonId, sectionId);
        // 如果命中，直接返回
        if(record != null){
            return record;
        }
        // 如果未命中，查询数据库
        record = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .eq(LearningRecord::getSectionId, sectionId)
                .one();
        // 写入缓存
        taskHandler.writeRecordCache(record);

        return record;
    }


    private Boolean handleExamRecord(Long userId, LearningRecordFormDTO recordDTO) {
        // 将DTO转换为PO
        LearningRecord record = BeanUtils.copyBean(recordDTO, LearningRecord.class);
        // 填充数据
        record.setUserId(userId);
        record.setFinished(true);
        record.setFinishTime(recordDTO.getCommitTime());
        // 保存到数据库
        boolean success = save(record);
        if(!success){
            throw new DbException("新增考试记录失败");
        }

        return true;

    }
}
