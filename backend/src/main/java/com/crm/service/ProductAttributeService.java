package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.entity.*;
import com.crm.mapper.ProductAttributeMapper;
import com.crm.mapper.ProductMapper;
import com.crm.vo.ProductAttributeVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductAttributeService {
    private final ProductAttributeMapper mapper;
    private final ProductMapper products;
    private final ObjectMapper json;

    public List<ProductAttributeVO> template(Long categoryId,String scope) {
        return definitions(categoryId,scope).stream().filter(a->a.getStatus()==1).toList();
    }

    public List<ProductAttributeVO> definitions(Long categoryId,String scope) {
        return preview(categoryId,scope,null,null);
    }

    // 配置保存前预览完整继承结果，用于检查下级分类的显示条件。
    List<ProductAttributeVO> preview(Long categoryId,String scope,ProductAttribute replacement,Long removedId) {
        List<Long> chain=new ArrayList<>();
        for (Long id=categoryId;id!=null;) {
            ProductCategory category=products.category(id);
            if (category==null) throw new BusinessException("产品分类不存在");
            chain.add(id);
            id=category.getParentId();
        }
        Collections.reverse(chain);
        Map<String,ProductAttributeVO> fields=new LinkedHashMap<>();
        for (Long id:chain) {
            List<ProductAttribute> local=new ArrayList<>(mapper.definitions(id));
            local.removeIf(a->a.getId().equals(removedId) || replacement!=null && a.getId().equals(replacement.getId()));
            if (replacement!=null && id.equals(replacement.getCategoryId())) local.add(replacement);
            for (ProductAttribute attribute:local) {
                if (!scope.equals(attribute.getAttributeScope())) continue;
                ProductAttributeVO field=new ProductAttributeVO();
                BeanUtils.copyProperties(attribute,field);
                field.setSourceCategoryName(products.category(id).getCategoryName());
                field.setInherited(!id.equals(categoryId));
                if (attribute.getOptionsJson()!=null) field.setOptions(read(attribute.getOptionsJson(),new TypeReference<List<String>>(){}));
                fields.put(field.getAttributeCode(),field);
            }
        }
        return fields.values().stream().sorted(Comparator.comparing(ProductAttributeVO::getSortNo)
                .thenComparing(ProductAttributeVO::getAttributeCode)).toList();
    }

    public Map<String,Object> values(Long productId,Long skuId,List<ProductAttributeVO> fields) {
        Map<String,Object> stored=new LinkedHashMap<>();
        mapper.values(productId,skuId).forEach(v->stored.put(v.getAttributeCode(),read(v.getValueJson(),new TypeReference<Object>(){})));
        Set<String> codes=new HashSet<>();
        fields.forEach(f->codes.add(f.getAttributeCode()));
        stored.keySet().retainAll(codes);
        fields.stream().filter(f->!visible(f,stored)).forEach(f->stored.remove(f.getAttributeCode()));
        return stored;
    }

    public Map<String,Object> normalize(List<ProductAttributeVO> fields,Map<String,Object> input) {
        return normalize(fields,input,true);
    }

    public Map<String,Object> normalizeSku(List<ProductAttributeVO> fields,Map<String,Object> input,ProductSku legacy) {
        Map<String,Object> supplied=input;
        if (supplied==null) {
            supplied=new HashMap<>();
            Map<String,Object> old=new HashMap<>();
            old.put("color",legacy.getColor()); old.put("hand_style",legacy.getHandStyle()); old.put("packaging",legacy.getPackaging());
            for (ProductAttributeVO field:fields) if (old.get(field.getAttributeCode())!=null) supplied.put(field.getAttributeCode(),old.get(field.getAttributeCode()));
        }
        return normalize(fields,supplied,true);
    }

    public void saveModel(Long productId,List<ProductAttributeVO> fields,Map<String,Object> values) {
        save(productId,null,fields,values);
    }

    public void clearModel(Long productId) {mapper.deleteValues(productId,null);}

    public void saveSku(Long skuId,List<ProductAttributeVO> fields,Map<String,Object> values) {
        save(null,skuId,fields,values);
    }

    public void reconcileSku(Long skuId,Long categoryId) {
        List<ProductAttributeVO> fields=template(categoryId,"SKU");
        Map<String,Object> old=values(null,skuId,fields),compatible=new LinkedHashMap<>();
        for (ProductAttributeVO field:fields) {
            try {putValue(field,old,compatible);}
            catch (BusinessException ignored) { /* 更换品类时丢弃类型或选项不匹配的旧参数。 */ }
        }
        mapper.deleteValues(null,skuId);
        save(null,skuId,fields,normalize(fields,compatible,false));
    }

    public List<String> missing(List<ProductAttributeVO> fields,Map<String,Object> values) {
        return fields.stream().filter(f->f.getRequired() && visible(f,values) && empty(values.get(f.getAttributeCode())))
                .map(ProductAttributeVO::getAttributeName).toList();
    }

    public String constructionSummary(Map<String,Object> values) {
        List<Object> structure=Arrays.asList(values.get("top_construction"),values.get("back_construction"),values.get("side_construction"));
        if (structure.contains(null)) return "待完善";
        if (structure.equals(List.of("单板","单板","单板"))) return "全单";
        if (structure.equals(List.of("单板","单板","合板"))) return "面背单";
        if (structure.equals(List.of("单板","合板","合板"))) return "面单";
        if (structure.equals(List.of("合板","合板","合板"))) return "合板";
        return "其他组合";
    }

    private Map<String,Object> normalize(List<ProductAttributeVO> fields,Map<String,Object> input,boolean required) {
        Map<String,Object> supplied=input==null?Map.of():input;
        Set<String> codes=new HashSet<>();
        fields.forEach(f->codes.add(f.getAttributeCode()));
        if (!codes.containsAll(supplied.keySet())) throw new BusinessException("包含不属于该品类的属性，请重新选择分类并检查参数");
        Map<String,Object> result=new LinkedHashMap<>();
        // Normalize controlling fields first, then conditionally visible fields.
        for (ProductAttributeVO field:fields) if (field.getConditionCode()==null) putValue(field,supplied,result);
        for (ProductAttributeVO field:fields) if (field.getConditionCode()!=null && visible(field,result)) putValue(field,supplied,result);
        List<String> missing=missing(fields,result);
        if (required && !missing.isEmpty()) throw new BusinessException("请完善属性："+String.join("、",missing));
        return result;
    }

    private void putValue(ProductAttributeVO field,Map<String,Object> input,Map<String,Object> result) {
        Object value=input.get(field.getAttributeCode());
        if (empty(value)) return;
        String message=field.getAttributeName()+"格式或选项不正确";
        switch (field.getInputType()) {
            case "NUMBER" -> {
                if (!(value instanceof Number)) throw new BusinessException(message);
                BigDecimal number=new BigDecimal(value.toString()).stripTrailingZeros();
                if (number.signum()<0 || number.scale()>field.getDecimalPlaces() || number.compareTo(new BigDecimal("1000000000"))>0) throw new BusinessException(message);
                value=number;
            }
            case "SELECT" -> {
                if (!(value instanceof String) || !field.getOptions().contains(value)) throw new BusinessException(message);
            }
            case "MULTI" -> {
                if (!(value instanceof List<?> list) || !field.getOptions().containsAll(list)) throw new BusinessException(message);
                value=new ArrayList<>(new LinkedHashSet<>((List<?>)value));
            }
            case "TEXT" -> {
                if (!(value instanceof String text) || text.length()>200) throw new BusinessException(message);
                value=((String)value).trim();
            }
            default -> throw new BusinessException("不支持的属性类型");
        }
        result.put(field.getAttributeCode(),value);
    }

    private boolean visible(ProductAttributeVO field,Map<String,Object> values) {
        return field.getConditionCode()==null || Objects.equals(field.getConditionValue(),values.get(field.getConditionCode()));
    }

    private boolean empty(Object value) {
        return value==null || value instanceof String text && text.isBlank() || value instanceof Collection<?> list && list.isEmpty();
    }

    private void save(Long productId,Long skuId,List<ProductAttributeVO> fields,Map<String,Object> values) {
        List<ProductAttributeValue> rows=new ArrayList<>();
        for (ProductAttributeVO field:fields) {
            Object value=values.get(field.getAttributeCode());
            if (value==null) continue;
            ProductAttributeValue row=new ProductAttributeValue();
            row.setAttributeId(field.getId()); row.setValueJson(write(value)); rows.add(row);
        }
        // 只替换当前可编辑字段，停用字段的历史值继续保留。
        if (!fields.isEmpty()) mapper.deleteEditableValues(productId,skuId,fields.stream().map(ProductAttributeVO::getAttributeCode).toList());
        if (!rows.isEmpty()) mapper.insertValues(productId,skuId,rows);
    }

    private <T> T read(String value,TypeReference<T> type) {
        try {return json.readValue(value,type);} catch (JsonProcessingException e) {throw new IllegalStateException("属性JSON格式异常",e);}
    }
    private String write(Object value) {
        try {return json.writeValueAsString(value);} catch (JsonProcessingException e) {throw new IllegalStateException(e);}
    }
}
