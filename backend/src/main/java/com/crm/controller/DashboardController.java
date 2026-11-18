package com.crm.controller;

import com.crm.common.ApiResult;
import com.crm.common.PageResult;
import com.crm.dto.PageQuery;
import com.crm.dto.ScheduleRequest;
import com.crm.entity.*;
import com.crm.service.DashboardService;
import com.crm.vo.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService service;

    @GetMapping("/overview")
    public ApiResult<WorkbenchVO> overview(@AuthenticationPrincipal User user) {
        return ApiResult.success(service.overview(user));
    }

    @GetMapping("/reports/{kind}")
    @PreAuthorize("hasAuthority('report:' + #kind + ':list')")
    public ApiResult<ReportVO> report(@AuthenticationPrincipal User user,@PathVariable String kind,
            @RequestParam LocalDate start,@RequestParam LocalDate end) {
        return ApiResult.success(service.report(user,kind,start,end));
    }

    @GetMapping("/schedules")
    public ApiResult<List<Schedule>> schedules(@AuthenticationPrincipal User user,@RequestParam LocalDate start,@RequestParam LocalDate end) {
        return ApiResult.success(service.schedules(user,start,end));
    }

    @GetMapping("/tasks")
    public ApiResult<PageResult<WorkItemVO>> tasks(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue="ALL") String type,@RequestParam(defaultValue="ALL") String due,
            @RequestParam(required=false) String keyword,@Valid PageQuery page) {
        return ApiResult.success(service.tasks(user,type,due,keyword,page));
    }

    @GetMapping("/agenda")
    public ApiResult<List<WorkItemVO>> agenda(@AuthenticationPrincipal User user,@RequestParam LocalDate start,@RequestParam LocalDate end) {
        return ApiResult.success(service.agenda(user,start,end));
    }

    @PostMapping("/schedules")
    public ApiResult<Long> create(@AuthenticationPrincipal User user,@Valid @RequestBody ScheduleRequest request) {
        return ApiResult.success(service.saveSchedule(user,null,request));
    }

    @PutMapping("/schedules/{id}")
    public ApiResult<Long> update(@AuthenticationPrincipal User user,@PathVariable Long id,@Valid @RequestBody ScheduleRequest request) {
        return ApiResult.success(service.saveSchedule(user,id,request));
    }
}
