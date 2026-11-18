package com.crm.common;

import java.util.List;

public record PageResult<T>(List<T> records,long total) {}
