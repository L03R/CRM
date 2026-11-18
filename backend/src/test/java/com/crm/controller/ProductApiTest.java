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
@WithMockUser(authorities={"product:list","product:add","product:edit","product:status","product:category:list","product:category:add","product:category:edit","product:category:delete"})
class ProductApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void initialCatalogIsRealAndIncludesVariants() throws Exception {
        mvc.perform(get("/api/products").param("keyword","XM-A200"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].brand").value("弦木乐器"));
        long id=jdbc.queryForObject("SELECT id FROM crm_product WHERE model_code='XM-A200'",Long.class);
        mvc.perform(get("/api/products/{id}",id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.skus.length()").value(2))
                .andExpect(jsonPath("$.data.retailPrice").isEmpty());
    }

    @Test
    void createUpdateAndOffShelfProductKeepsModelAndSkus() throws Exception {
        long category=category(null);
        Map<String,Object> request=productRequest(category);
        long id=createProduct(request);
        request.put("productName","更新后的产品");
        mvc.perform(put("/api/products/{id}",id).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk());
        long sku=createSku(id,skuRequest());
        mvc.perform(put("/api/products/{id}/status",id).contentType("application/json").content("{\"status\":0}")).andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}",id)).andExpect(jsonPath("$.data.available").value(false)).andExpect(jsonPath("$.data.skus[0].id").value(sku));
        request.put("modelCode",code());
        mvc.perform(put("/api/products/{id}",id).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/products/categories/{id}",category)).andExpect(status().isBadRequest());
    }

    @Test
    void categoryCycleInvalidLeafAndDisabledAncestorsAreRejected() throws Exception {
        long root=category(null);
        long child=category(root);
        String code=jdbc.queryForObject("SELECT category_code FROM crm_product_category WHERE id=?",String.class,root);
        mvc.perform(put("/api/products/categories/{id}",root).contentType("application/json")
                .content(json.writeValueAsString(categoryRequest(child,code,1)))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(productRequest(root)))).andExpect(status().isBadRequest());
        long product=createProduct(productRequest(child));
        mvc.perform(put("/api/products/categories/{id}",root).contentType("application/json")
                .content(json.writeValueAsString(categoryRequest(null,code,0)))).andExpect(status().isOk());
        mvc.perform(get("/api/products/{id}",product)).andExpect(jsonPath("$.data.available").value(false));
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(productRequest(child)))).andExpect(status().isBadRequest());
    }

    @Test
    void categorySearchIncludesDescendantsAndEmptyCategoryCanBeDeleted() throws Exception {
        long root=category(null);
        long leaf=category(root);
        createProduct(productRequest(leaf));
        mvc.perform(get("/api/products").param("categoryId",Long.toString(root))).andExpect(jsonPath("$.data.length()").value(1));
        mvc.perform(delete("/api/products/categories/{id}",root)).andExpect(status().isBadRequest());
        long empty=category(null);
        mvc.perform(delete("/api/products/categories/{id}",empty)).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("SELECT deleted FROM crm_product_category WHERE id=?",Integer.class,empty));
    }

    @Test
    void duplicateAndNegativePricesRejectedAndSkuMustBelongToProduct() throws Exception {
        long category=category(null);
        Map<String,Object> model=productRequest(category);
        long first=createProduct(model);
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(model))).andExpect(status().isConflict());
        model.put("modelCode",code()); model.put("retailPrice",-1);
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(model))).andExpect(status().isBadRequest());
        long second=createProduct(productRequest(category));
        Map<String,Object> sku=skuRequest();
        long id=createSku(first,sku);
        mvc.perform(post("/api/products/{id}/skus",first).contentType("application/json").content(json.writeValueAsString(sku))).andExpect(status().isConflict());
        mvc.perform(put("/api/products/{id}/skus/{skuId}",second,id).contentType("application/json").content(json.writeValueAsString(sku))).andExpect(status().isBadRequest());
        mvc.perform(put("/api/products/{id}/skus/{skuId}/status",first,id).contentType("application/json").content("{\"status\":0}")).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("SELECT status FROM crm_product_sku WHERE id=?",Integer.class,id));
    }

    @Test
    @WithMockUser(authorities="product:list")
    void readerCanBrowseButCannotMaintain() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/api/products/categories")).andExpect(status().isOk());
        long category=jdbc.queryForObject("SELECT id FROM crm_product_category WHERE category_code='ACOUSTIC_FOLK'",Long.class);
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(productRequest(category)))).andExpect(status().isForbidden());
        mvc.perform(put("/api/products/1/status").contentType("application/json").content("{\"status\":0}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities="order:list")
    void orderPermissionDoesNotImplyProductPermission() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isForbidden());
    }

    private long category(Long parent) throws Exception {
        return id(mvc.perform(post("/api/products/categories").contentType("application/json").content(json.writeValueAsString(categoryRequest(parent,code(),1)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private Map<String,Object> categoryRequest(Long parent,String code,int status){
        Map<String,Object> request=new HashMap<>();request.put("parentId",parent);request.put("categoryName","测试分类");request.put("categoryCode",code);request.put("sortNo",0);request.put("status",status);return request;
    }
    private Map<String,Object> productRequest(long category){
        return new HashMap<>(Map.of("categoryId",category,"modelCode",code(),"productName","测试产品","brand","弦木乐器","supplyType",1,"unit","把"));
    }
    private Map<String,Object> skuRequest(){return new HashMap<>(Map.of("skuCode",code(),"color","原木色","handStyle","右手","packaging","单把","salePrice",100));}
    private long createProduct(Map<String,Object> request) throws Exception {
        return id(mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private long createSku(long product,Map<String,Object> request) throws Exception {
        return id(mvc.perform(post("/api/products/{id}/skus",product).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private long id(String response) throws Exception {return json.readTree(response).path("data").asLong();}
    private String code(){return "T_"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();}
}
