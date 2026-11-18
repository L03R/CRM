package com.crm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities={"product:list","product:add","product:edit","product:category:list","product:category:edit"})
class ProductAttributeManageApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void newCategoryTemplateDrivesRealModelAndSkuCreation() throws Exception {
        long category=category(null);
        Map<String,Object> power=field("MODEL","power","NUMBER");power.put("unit","W");power.put("required",true);
        add(category,power);
        Map<String,Object> colors=field("SKU","color","SELECT");colors.put("options",List.of("黑色","白色"));colors.put("required",true);
        add(category,colors);
        JsonNode template=data(mvc.perform(get("/api/products/categories/{id}/attributes",category)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals("power",template.path("model").get(0).path("attributeCode").asText());
        assertEquals("color",template.path("sku").get(0).path("attributeCode").asText());
        Map<String,Object> request=product(category,Map.of());
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        request.put("attributes",Map.of("power",50));
        long id=createProduct(request);
        mvc.perform(post("/api/products/{id}/skus",id).contentType("application/json").content(json.writeValueAsString(Map.of("skuCode",code(),"attributes",Map.of("color","黑色"))))).andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}",id)).andExpect(jsonPath("$.data.attributes.power").value(50)).andExpect(jsonPath("$.data.skus[0].attributes.color").value("黑色"));
    }

    @Test
    void inheritsSourceAndLocalOverrideCanBeDeletedToRestoreParent() throws Exception {
        long root=category(null),child=category(root);
        Map<String,Object> request=field("MODEL","material","TEXT");
        long parentId=add(root,request);
        JsonNode inherited=definitions(child).path("model").get(0);
        assertTrue(inherited.path("inherited").asBoolean());
        assertEquals(parentId,inherited.path("id").asLong());
        assertFalse(inherited.path("sourceCategoryName").asText().isBlank());
        request.put("attributeName","本级材料");
        long override=add(child,request);
        assertFalse(definitions(child).path("model").get(0).path("inherited").asBoolean());
        assertEquals("本级材料",definitions(child).path("model").get(0).path("attributeName").asText());
        mvc.perform(delete(path(child)+"/{id}",override)).andExpect(status().isOk());
        assertEquals(parentId,definitions(child).path("model").get(0).path("id").asLong());
    }

    @Test
    void usedAttributeProtectsHistoryButAllowsNamesRequiredAndAdditionalOptions() throws Exception {
        long root=category(null),child=category(root);
        Map<String,Object> request=field("MODEL","mode","SELECT");request.put("options",List.of("A","B"));
        long id=add(root,request);
        long product=createProduct(product(child,Map.of("mode","A")));
        assertTrue(definitions(root).path("model").get(0).path("used").asBoolean());
        request.put("inputType","TEXT");update(root,id,request,400);request.put("inputType","SELECT");
        request.put("options",List.of("A"));update(root,id,request,400);
        mvc.perform(delete(path(root)+"/{id}",id)).andExpect(status().isBadRequest());
        request.put("options",List.of("A","B","C"));request.put("attributeName","模式");request.put("required",true);request.put("sortNo",9);
        update(root,id,request,200);
        mvc.perform(get("/api/products/{id}",product)).andExpect(jsonPath("$.data.attributes.mode").value("A"));
        request.put("status",0);update(root,id,request,200);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_product_attribute_value WHERE attribute_id=?",Integer.class,id));
        request.put("status",1);update(root,id,request,200);
        mvc.perform(get("/api/products/{id}",product)).andExpect(jsonPath("$.data.attributes.mode").value("A"));
    }

    @Test
    void usedInheritedAttributeCannotBeOverriddenWithIncompatibleTypeOrPrecision() throws Exception {
        long root=category(null),child=category(root);
        Map<String,Object> request=field("MODEL","power","NUMBER");add(root,request);
        createProduct(product(child,Map.of("power",12.5)));
        request.put("decimalPlaces",0);addStatus(child,request,400);
        request.put("decimalPlaces",2);request.put("inputType","TEXT");addStatus(child,request,400);
        request.put("inputType","NUMBER");request.put("attributeName","本级功率");add(child,request);
        assertEquals("本级功率",definitions(child).path("model").get(0).path("attributeName").asText());
    }

    @Test
    void conditionsValidateAcrossDescendantsAndPreventBreakingDependentFields() throws Exception {
        long root=category(null),child=category(root);
        Map<String,Object> control=field("MODEL","mode","SELECT");control.put("options",List.of("有","无"));
        long id=add(root,control);
        Map<String,Object> dependent=field("MODEL","detail","TEXT");dependent.put("conditionCode","mode");dependent.put("conditionValue","有");
        long detail=add(child,dependent);
        control.put("status",0);update(root,id,control,400);control.put("status",1);
        control.put("options",List.of("无"));update(root,id,control,400);
        mvc.perform(delete(path(root)+"/{id}",id)).andExpect(status().isBadRequest());
        dependent.put("conditionCode","detail");update(child,detail,dependent,400);
        dependent.put("conditionCode","mode");dependent.put("conditionValue","不存在");update(child,detail,dependent,400);
        dependent.put("conditionValue","有");dependent.put("status",0);update(child,detail,dependent,200);
        control.put("options",List.of("有","无"));control.put("status",0);update(root,id,control,200);
    }

    @Test
    void rejectsDuplicateInvalidOptionsConditionsAndImmutableIdentityBeforeWriting() throws Exception {
        long category=category(null);
        Map<String,Object> request=field("MODEL","kind","SELECT");
        addStatus(category,request,400);
        request.put("options",List.of("A"," A "));addStatus(category,request,400);
        request.put("options",List.of("A"));request.put("conditionCode","other");addStatus(category,request,400);
        request.put("conditionCode",null);request.put("decimalPlaces",7);addStatus(category,request,400);
        request.put("decimalPlaces",2);long id=add(category,request);
        addStatus(category,request,400);
        request.put("attributeCode","changed");update(category,id,request,400);
        request.put("attributeCode","kind");request.put("attributeScope","SKU");update(category,id,request,400);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM crm_category_attribute WHERE category_id=?",Integer.class,category));
    }

    @Test
    void cannotEditAnotherCategoryOrDeletedCategory() throws Exception {
        long root=category(null),other=category(null);
        Map<String,Object> request=field("MODEL","material","TEXT");long id=add(root,request);
        update(other,id,request,400);
        mvc.perform(delete(path(other)+"/{id}",id)).andExpect(status().isBadRequest());
        jdbc.update("UPDATE crm_product_category SET deleted=1 WHERE id=?",other);
        addStatus(other,request,400);
    }

    @Test
    void newRequiredFieldMarksExistingModelIncompleteWithoutChangingItsStatus() throws Exception {
        long category=category(null),product=createProduct(product(category,Map.of()));
        Map<String,Object> request=field("MODEL","power","NUMBER");request.put("required",true);add(category,request);
        mvc.perform(get("/api/products/{id}",product)).andExpect(jsonPath("$.data.missingAttributes[0]").value("power"))
                .andExpect(jsonPath("$.data.status").value(1));
    }

    @Test
    void categoryChangeDropsSkuValuesWithIncompatibleTypeEvenWhenCodeMatches() throws Exception {
        long oldCategory=category(null),newCategory=category(null);
        add(oldCategory,field("SKU","size","TEXT"));add(newCategory,field("SKU","size","NUMBER"));
        long id=createProduct(product(oldCategory,Map.of()));
        mvc.perform(post("/api/products/{id}/skus",id).contentType("application/json").content(json.writeValueAsString(Map.of("skuCode",code(),"attributes",Map.of("size","大号"))))).andExpect(status().isOk());
        Map<String,Object> request=json.convertValue(data(mvc.perform(get("/api/products/{id}",id)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)),Map.class);
        request.put("categoryId",newCategory);
        mvc.perform(put("/api/products/{id}",id).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}",id)).andExpect(jsonPath("$.data.skus[0].attributes.size").doesNotExist());
    }

    @Test
    @WithMockUser(authorities="product:category:list")
    void readOnlyCategoryPermissionDoesNotAllowTemplateWrites() throws Exception {
        long category=category(null);
        definitions(category);
        addStatus(category,field("MODEL","material","TEXT"),403);
        mvc.perform(put(path(category)+"/1").contentType("application/json").content(json.writeValueAsString(field("MODEL","material","TEXT")))).andExpect(status().isForbidden());
        mvc.perform(delete(path(category)+"/1")).andExpect(status().isForbidden());
    }

    @Test
    void savingModelAndSkuWhileFieldDisabledPreservesValuesForReactivation() throws Exception {
        long category=category(null);
        Map<String,Object> material=field("MODEL","material","TEXT"),color=field("SKU","color","TEXT");
        long materialId=add(category,material),colorId=add(category,color);
        long product=createProduct(product(category,Map.of("material","桃花心木")));
        long sku=data(mvc.perform(post("/api/products/{id}/skus",product).contentType("application/json").content(json.writeValueAsString(Map.of("skuCode",code(),"attributes",Map.of("color","原木色"))))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).asLong();
        material.put("status",0);color.put("status",0);update(category,materialId,material,200);update(category,colorId,color,200);
        JsonNode detail=data(mvc.perform(get("/api/products/{id}",product)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        Map<String,Object> edit=json.convertValue(detail,Map.class);edit.put("productName","只修改名称");
        mvc.perform(put("/api/products/{id}",product).contentType("application/json").content(json.writeValueAsString(edit))).andExpect(status().isOk());
        mvc.perform(put("/api/products/{id}/skus/{skuId}",product,sku).contentType("application/json").content(detail.path("skus").get(0).toString())).andExpect(status().isOk());
        material.put("status",1);color.put("status",1);update(category,materialId,material,200);update(category,colorId,color,200);
        mvc.perform(get("/api/products/{id}",product)).andExpect(jsonPath("$.data.attributes.material").value("桃花心木"))
                .andExpect(jsonPath("$.data.skus[0].attributes.color").value("原木色"));
    }

    @Test
    @WithMockUser(authorities={"product:list","product:add","product:category:edit"})
    void categoryBranchContainingProductsCannotChangeParent() throws Exception {
        long root=category(null),leaf=category(root),other=category(null);
        createProduct(product(leaf,Map.of()));
        String categoryCode=jdbc.queryForObject("SELECT category_code FROM crm_product_category WHERE id=?",String.class,root);
        mvc.perform(put("/api/products/categories/{id}",root).contentType("application/json").content(json.writeValueAsString(Map.of("categoryName","移动分类","categoryCode",categoryCode,"parentId",other,"sortNo",0,"status",1)))).andExpect(status().isBadRequest());
        assertNull(jdbc.queryForObject("SELECT parent_id FROM crm_product_category WHERE id=?",Long.class,root));
    }

    @Test
    void skuLegacyResponseFieldsAlwaysReflectDynamicAttributes() throws Exception {
        long category=category(null);add(category,field("SKU","color","TEXT"));
        long product=createProduct(product(category,Map.of()));
        String longColor="原木色".repeat(20);
        mvc.perform(post("/api/products/{id}/skus",product).contentType("application/json").content(json.writeValueAsString(Map.of("skuCode",code(),"attributes",Map.of("color",longColor))))).andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}",product)).andExpect(jsonPath("$.data.skus[0].color").value(longColor)).andExpect(jsonPath("$.data.skus[0].attributes.color").value(longColor));
        assertNull(jdbc.queryForObject("SELECT color FROM crm_product_sku WHERE product_id=?",String.class,product));
    }

    private Map<String,Object> field(String scope,String code,String type) {
        Map<String,Object> request=new LinkedHashMap<>();
        request.put("attributeScope",scope);request.put("attributeCode",code);request.put("attributeName",code);request.put("inputType",type);
        request.put("options",List.of());request.put("required",false);request.put("decimalPlaces",2);request.put("sortNo",0);request.put("status",1);
        return request;
    }
    private long category(Long parent) {
        String code=code();jdbc.update("INSERT INTO crm_product_category(parent_id,category_code,category_name) VALUES(?,?,?)",parent,code,"测试分类"+code);
        return jdbc.queryForObject("SELECT id FROM crm_product_category WHERE category_code=?",Long.class,code);
    }
    private long add(long category,Map<String,Object> request) throws Exception {
        return data(mvc.perform(post(path(category)).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).asLong();
    }
    private void addStatus(long category,Map<String,Object> request,int status) throws Exception {mvc.perform(post(path(category)).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().is(status));}
    private void update(long category,long id,Map<String,Object> request,int status) throws Exception {mvc.perform(put(path(category)+"/{id}",id).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().is(status));}
    private JsonNode definitions(long category) throws Exception {return data(mvc.perform(get(path(category))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));}
    private Map<String,Object> product(long category,Map<String,Object> attributes) {return new HashMap<>(Map.of("categoryId",category,"modelCode",code(),"productName","配置测试产品","brand","弦木乐器","supplyType",1,"unit","台","attributes",attributes));}
    private long createProduct(Map<String,Object> request) throws Exception {return data(mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).asLong();}
    private JsonNode data(String response) throws Exception {return json.readTree(response).path("data");}
    private String path(long id) {return "/api/products/categories/"+id+"/attribute-definitions";}
    private String code() {return "T_"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();}
}
