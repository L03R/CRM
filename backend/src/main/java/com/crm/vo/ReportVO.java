package com.crm.vo;
import java.util.List;
import java.util.Map;
public record ReportVO(List<ChartPointVO> distribution, List<ChartPointVO> trend, List<ChartPointVO> sources, Map<String,Long> metrics) {}
