package com.tianji.learning.utils;

import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import io.lettuce.core.resource.Delay;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;

@Slf4j
@RequiredArgsConstructor
@Component
public class LearningRecordDelayTaskHandler {

    private final StringRedisTemplate redisTemplate;

    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();

    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";

    private final LearningRecordMapper recordMapper;

    private final ILearningLessonService lessonService;

    private static volatile boolean begin = true;


    @PostConstruct
    public void init(){
        // 异步执行
        CompletableFuture.runAsync(this::handleDelayTask);
    }

    @PreDestroy
    public void destroy(){
        begin = false;
        log.debug("延迟任务停止执行");
    }

    public void handleDelayTask(){
        while (begin){
            try {
                // 获取到期的延迟任务
                DelayTask<RecordTaskData> task = queue.take();
                RecordTaskData data = task.getData();
                // 查询redis数据
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if(record == null){
                    continue;
                }
                // 比较数据中的moment值
                if(!Objects.equals(data.getMoment(),record.getMoment())){
                    // 不一致，说明用户还在持续提交播放记录，不用管
                    continue;
                }
                // 一致，将播放记录持久化到数据库
                // 更新学习记录
                record.setFinished(null);
                recordMapper.updateById(record);
                // 更新课表最近学习小节、时间
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);
            } catch (Exception e) {
                log.error("处理延迟任务发生异常",e);
            }

        }
    }

    public void addLearningRecordTask(LearningRecord record){
        // 添加数据到redis中
        writeRecordCache(record);
        // 提交延迟任务到延迟队列DelayQueue中
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            // 数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 写入redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,record.getLessonId());
            redisTemplate.opsForHash().put(key, record.getSectionId().toString(), json);
            // 添加过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常",e);
        }
    }


    public LearningRecord readRecordCache(Long lessonId, Long sectionId){
        try {
            // 读取redis数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
            Object cacheData = redisTemplate.opsForHash().get(key, sectionId.toString());
            if (cacheData == null) {
                return null;
            }
            // 数据转换
            return JsonUtils.toBean(cacheData.toString(),LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常",e);
            return null;
        }
    }


    public void cleanRecordCache(Long lessonId, Long sectionId){
        // 删除数据
        String key = StringUtils.format(RECORD_KEY_TEMPLATE,lessonId);
        redisTemplate.opsForHash().delete(key,sectionId.toString());
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheData{
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskData{
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}
