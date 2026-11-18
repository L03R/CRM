package com.crm.service;

import com.crm.common.BusinessException;
import com.crm.dto.ProductAttributeRequest;
import com.crm.entity.ProductAttribute;
import com.crm.mapper.ProductAttributeMapper;
import com.crm.mapper.ProductMapper;
import com.crm.vo.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductAttributeManageService {
    private final ProductAttributeMapper mapper;
    private final ProductMapper products;
    private final ProductAttributeService attributes;
    private final ObjectMapper json;

    public void checkCategoryMove(Long categoryId) {
        if (descendants(categoryId).stream().anyMatch(id->products.countProducts(id)>0))
            throw new BusinessException("含有产品的分类分支不能更换上级，请先迁移产品，避免改变已有产品的属性模板");
    }

    public void validateHierarchy(Long categoryId) {
        List<Long> ids=descendants(categoryId);
        validateTemplates(ids,null,null,"MODEL");
        validateTemplates(ids,null,null,"SKU");
    }

    public ProductAttributeTemplateVO list(Long categoryId) {
        return new ProductAttributeTemplateVO(list(categoryId,"MODEL"),list(categoryId,"SKU"));
    }

    private List<ProductAttributeVO> list(Long categoryId,String scope) {
        List<Long> ids=descendants(categoryId);
        List<ProductAttributeVO> fields=attributes.definitions(categoryId,scope);
        fields.forEach(f->f.setUsed(mapper.countReferences(f.getId())>0 || mapper.countUsage(ids,scope,f.getAttributeCode())>0));
        return fields;
    }

    @Transactional
    public Long create(Long categoryId,ProductAttributeRequest request) {
        products.lockCategories();
        List<Long> ids=descendants(categoryId);
        ProductAttribute field=toEntity(categoryId,request);
        if (mapper.definitions(categoryId).stream().anyMatch(a->a.getAttributeScope().equals(field.getAttributeScope()) && a.getAttributeCode().equals(field.getAttributeCode())))
            throw new BusinessException("本分类已有相同范围和编码的属性，请编辑原属性");
        ProductAttributeVO inherited=attributes.definitions(categoryId,field.getAttributeScope()).stream()
                .filter(a->a.getAttributeCode().equals(field.getAttributeCode())).findFirst().orElse(null);
        if (inherited!=null && mapper.countUsage(ids,field.getAttributeScope(),field.getAttributeCode())>0) checkUsed(inherited,field,request.options());
        validateTemplates(ids,field,null,field.getAttributeScope());
        mapper.insertDefinition(field);
        return field.getId();
    }

    @Transactional
    public void update(Long categoryId,Long id,ProductAttributeRequest request) {
        products.lockCategories();
        List<Long> ids=descendants(categoryId);
        ProductAttribute existing=local(categoryId,id);
        ProductAttribute field=toEntity(categoryId,request);
        field.setId(id);
        if (!existing.getAttributeCode().equals(field.getAttributeCode()) || !existing.getAttributeScope().equals(field.getAttributeScope()))
            throw new BusinessException("属性编码和范围不能修改");
        if (mapper.countReferences(id)>0 || mapper.countUsage(ids,field.getAttributeScope(),field.getAttributeCode())>0) {
            ProductAttributeVO old=attributes.definitions(categoryId,field.getAttributeScope()).stream().filter(a->a.getId().equals(id)).findFirst().orElseThrow();
            checkUsed(old,field,request.options());
        }
        validateTemplates(ids,field,null,field.getAttributeScope());
        mapper.updateDefinition(field);
    }

    @Transactional
    public void delete(Long categoryId,Long id) {
        products.lockCategories();
        List<Long> ids=descendants(categoryId);
        ProductAttribute field=local(categoryId,id);
        if (mapper.countReferences(id)>0 || mapper.countUsage(ids,field.getAttributeScope(),field.getAttributeCode())>0)
            throw new BusinessException("属性已有产品或SKU数据，不能删除，可改为停用");
        validateTemplates(ids,null,id,field.getAttributeScope());
        mapper.deleteDefinition(id);
    }

    private ProductAttribute local(Long categoryId,Long id) {
        ProductAttribute field=mapper.definition(id);
        if (field==null || !field.getCategoryId().equals(categoryId)) throw new BusinessException("本分类下的属性不存在；继承属性请到来源分类修改或在本级配置");
        return field;
    }

    private void checkUsed(ProductAttributeVO old,ProductAttribute field,List<String> options) {
        if (!old.getInputType().equals(field.getInputType()) || !old.getDecimalPlaces().equals(field.getDecimalPlaces())
                || !Objects.equals(old.getConditionCode(),field.getConditionCode()) || !Objects.equals(old.getConditionValue(),field.getConditionValue()))
            throw new BusinessException("已有数据的属性不能修改类型、精度或显示条件");
        if (Set.of("SELECT","MULTI").contains(old.getInputType()) && !options.containsAll(old.getOptions()))
            throw new BusinessException("已有数据的选择属性只能补充选项，不能删除原选项");
    }

    private void validateTemplates(List<Long> ids,ProductAttribute replacement,Long removedId,String scope) {
        for (Long id:ids) {
            Map<String,ProductAttributeVO> fields=new HashMap<>();
            attributes.preview(id,scope,replacement,removedId).stream().filter(f->f.getStatus()==1).forEach(f->fields.put(f.getAttributeCode(),f));
            for (ProductAttributeVO field:fields.values()) {
                if (field.getConditionCode()==null) continue;
                ProductAttributeVO control=fields.get(field.getConditionCode());
                if (control==null || control==field || !control.getInputType().equals("SELECT") || control.getConditionCode()!=null || !control.getOptions().contains(field.getConditionValue()))
                    throw new BusinessException("分类“"+products.category(id).getCategoryName()+"”的“"+field.getAttributeName()+"”显示条件无效：条件字段必须是启用且无条件的单选属性，条件值必须在选项中");
            }
        }
    }

    private List<Long> descendants(Long categoryId) {
        if (products.category(categoryId)==null) throw new BusinessException("产品分类不存在");
        List<Long> ids=new ArrayList<>(List.of(categoryId));
        List<ProductCategoryVO> all=products.categories();
        for (int i=0;i<ids.size();i++) for (ProductCategoryVO category:all) if (ids.get(i).equals(category.getParentId())) ids.add(category.getId());
        return ids;
    }

    private ProductAttribute toEntity(Long categoryId,ProductAttributeRequest request) {
        List<String> options=request.options().stream().map(String::trim).toList();
        if (new HashSet<>(options).size()!=options.size()) throw new BusinessException("属性选项不能重复");
        if (Set.of("SELECT","MULTI").contains(request.inputType()) && options.isEmpty()) throw new BusinessException("单选和多选属性至少填写一个选项");
        if (request.inputType().equals("NUMBER") && !options.isEmpty()) throw new BusinessException("数字属性不配置选项");
        String condition=blankToNull(request.conditionCode()),value=blankToNull(request.conditionValue());
        if ((condition==null)!=(value==null)) throw new BusinessException("显示条件的属性和选项必须一起填写");
        ProductAttribute field=new ProductAttribute();
        field.setCategoryId(categoryId); field.setAttributeScope(request.attributeScope()); field.setAttributeCode(request.attributeCode());
        field.setAttributeName(request.attributeName().trim()); field.setInputType(request.inputType());
        try {field.setOptionsJson(json.writeValueAsString(options));} catch (JsonProcessingException e) {throw new IllegalStateException(e);}
        field.setUnit(blankToNull(request.unit())); field.setRequired(request.required());
        field.setConditionCode(condition); field.setConditionValue(value); field.setDecimalPlaces(request.decimalPlaces());
        field.setSortNo(request.sortNo()); field.setStatus(request.status());
        return field;
    }

    private String blankToNull(String value) {return value==null || value.isBlank()?null:value.trim();}
}
