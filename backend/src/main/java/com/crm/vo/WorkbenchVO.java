package com.crm.vo;
import java.util.*;
public record WorkbenchVO(Map<String,Long> metrics, List<WorkItemVO> todos) {}
