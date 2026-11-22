package com.tianji.learning.handler;

import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.DateUtils;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import com.tianji.learning.utils.TableInfoContext;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PointsBoardPersistentHandler {

    private final IPointsBoardSeasonService seasonService;
    private final IPointsBoardService pointsBoardService;
    private final StringRedisTemplate redisTemplate;

    @XxlJob("createTableJob")
    public void createPointsBoardTableOfLastSeason(){
        // 获取上个月的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 查询赛季id
        Integer season = seasonService.querySeasonByTime(time);
        if (season == null) {
            return;
        }
        // 创建表
        pointsBoardService.createPointsBoardTableBySeason(season);
    }

    @XxlJob("savePointsBoard2DB")
    public void savePointsBoard2DB(){
        // 1.获取上个月的时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 2.计算动态表名，存入threadLocal
        Integer season = seasonService.querySeasonByTime(time);
        TableInfoContext.setInfo("points_board_" + season);

        // 3.redis中榜单数据
        // 3.1拼接redis中的key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 3.2查询数据
        int index = XxlJobHelper.getShardIndex();
        int total = XxlJobHelper.getShardTotal();
        int pageNo = index + 1;
        int pageSize = 1000;
        while (true){
            List<PointsBoard> boardList = pointsBoardService.queryCurrentBoardList(key, pageNo, pageSize);
            if(CollUtils.isEmpty(boardList)){
                break;
            }

            // 4.持久化到数据库
            // 4.1我们要持久化后的数据库只有三个字段，id即rank，userId，points，但我们查询得到的PO即PointsBoard有五个字段，需要进行处理转换
            boardList.forEach(b -> {
                b.setId(b.getRank().longValue());
                b.setRank(null);
            });
            // 4.2持久化
            pointsBoardService.saveBatch(boardList);

            // 5.翻页
            pageNo+=total;
        }
        TableInfoContext.remove();
    }

    @XxlJob("clearPointsBoard")
    public void clearPointsBoardFromRedis(){
        // 获取时间
        LocalDateTime time = LocalDateTime.now().minusMonths(1);
        // 计算key
        String key = RedisConstants.POINTS_BOARD_KEY_PREFIX + time.format(DateUtils.POINTS_BOARD_SUFFIX_FORMATTER);
        // 删除
        redisTemplate.unlink(key);
    }
}
