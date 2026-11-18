package com.crm.mapper;
import com.crm.entity.Schedule;
import com.crm.vo.*;
import com.crm.security.DataScope;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDate;
import java.util.List;
public interface DashboardMapper {
    List<ChartPointVO> groups(@Param("kind") String kind, @Param("group") String group,
            @Param("scope") DataScope scope, @Param("readScope") DataScope readScope,
            @Param("start") LocalDate start, @Param("end") LocalDate end);
    long customerDue(@Param("scope") DataScope scope,@Param("overdue") boolean overdue);
    List<WorkItemVO> todos(@Param("kind") String kind,@Param("scope") DataScope scope);
    long todoCount(@Param("kind") String kind,@Param("scope") DataScope scope);
    List<Schedule> schedules(@Param("userId") Long userId,@Param("start") LocalDate start,@Param("end") LocalDate end);
    Schedule schedule(@Param("id") Long id,@Param("userId") Long userId);
    void insertSchedule(Schedule schedule);
    void updateSchedule(Schedule schedule);
    List<WorkItemVO> tasks(@Param("scopes") java.util.Map<String,DataScope> scopes,@Param("type") String type,
            @Param("due") String due,@Param("keyword") String keyword,@Param("start") LocalDate start,@Param("end") LocalDate end,
            @Param("offset") int offset,@Param("size") int size);
    long taskCount(@Param("scopes") java.util.Map<String,DataScope> scopes,@Param("type") String type,
            @Param("due") String due,@Param("keyword") String keyword,@Param("start") LocalDate start,@Param("end") LocalDate end);
}
