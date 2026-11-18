package com.crm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(authorities={"product:list","product:add","product:edit","product:category:add"})
class ProductAttributeApiTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;

    @Test
    void templateInheritanceOverrideAndDisabledField() throws Exception {
        String code=code();
        jdbc.update("INSERT INTO crm_product_category(parent_id,category_code,category_name) SELECT id,?,'测试民谣分类' FROM crm_product_category WHERE category_code='GUITAR_ACOUSTIC'",code);
        long leaf=category(code);
        JsonNode template=template(leaf);
        assertTrue(codes(template.path("model")).contains("electronics"));
        assertTrue(codes(template.path("sku")).contains("hand_style"));
        jdbc.update("INSERT INTO crm_category_attribute(category_id,attribute_code,attribute_name,input_type,options_json,required) VALUES(?,'body_shape','自定义桶型','SELECT','[\"CUSTOM\"]',1)",leaf);
        jdbc.update("INSERT INTO crm_category_attribute(category_id,attribute_code,attribute_name,input_type,status) VALUES(?,'neck_material','不使用琴颈字段','TEXT',0)",leaf);
        template=template(leaf);
        assertFalse(codes(template.path("model")).contains("neck_material"));
        int count=0;
        for(JsonNode field:template.path("model")) if(field.path("attributeCode").asText().equals("body_shape")){
            count++;assertEquals("CUSTOM",field.path("options").get(0).asText());
        }
        assertEquals(1,count);
    }

    @Test
    void acousticAttributesPersistAndConstructionSummaryIsDerived() throws Exception {
        long id=create("ACOUSTIC_FOLK",acoustic());
        JsonNode product=detail(id);
        assertEquals("面单",product.path("constructionSummary").asText());
        assertEquals("D",product.path("attributes").path("body_shape").asText());
        assertEquals(41,product.path("attributes").path("body_size").asInt());
        assertTrue(product.path("missingAttributes").isEmpty());
        assertFalse(product.path("attributes").has("pickup_brand"));
    }

    @Test
    void electronicsAndCutawayRequireDependentFieldsAndInactiveValuesAreCleared() throws Exception {
        Map<String,Object> values=acoustic();
        values.put("electronics","电箱");
        assertCreateStatus("ACOUSTIC_FOLK",values,400);
        values.put("pickup_brand","Fishman");values.put("pickup_model","Presys+");
        values.put("cutaway","缺角");
        assertCreateStatus("ACOUSTIC_FOLK",values,400);
        values.put("cutaway_style","圆缺角");
        long id=create("ACOUSTIC_FOLK",values);
        JsonNode product=detail(id);
        assertEquals("Presys+",product.path("attributes").path("pickup_model").asText());
        Map<String,Object> request=json.convertValue(product,Map.class);
        values.put("electronics","纯原声");values.put("cutaway","无缺角");
        request.put("attributes",values);
        mvc.perform(put("/api/products/{id}",id).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk());
        product=detail(id);
        assertFalse(product.path("attributes").has("pickup_brand"));
        assertFalse(product.path("attributes").has("pickup_model"));
        assertFalse(product.path("attributes").has("cutaway_style"));
    }

    @Test
    void validatesRequiredFieldsTypesOptionsAndRejectsUnknownFieldsWithoutPartialWrites() throws Exception {
        int before=jdbc.queryForObject("SELECT COUNT(*) FROM crm_product",Integer.class);
        assertCreateStatus("ACOUSTIC_FOLK",Map.of(),400);
        Map<String,Object> values=acoustic();values.put("body_shape","NOT_A_SHAPE");
        assertCreateStatus("ACOUSTIC_FOLK",values,400);
        values=acoustic();values.put("body_size","41");
        assertCreateStatus("ACOUSTIC_FOLK",values,400);
        values=acoustic();values.put("unknown_field","value");
        assertCreateStatus("ACOUSTIC_FOLK",values,400);
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM crm_product",Integer.class));
    }

    @Test
    void numericAndMultiselectValuesAreTypedAndValidated() throws Exception {
        Map<String,Object> values=new HashMap<>(Map.of("instrument","木吉他","rated_power",30,"inputs",List.of("吉他","麦克风")));
        JsonNode amp=detail(create("AMP_ACOUSTIC",values));
        assertEquals(30,amp.path("attributes").path("rated_power").asInt());
        assertEquals(2,amp.path("attributes").path("inputs").size());
        values.put("rated_power",-30);assertCreateStatus("AMP_ACOUSTIC",values,400);
        values.put("rated_power",30);values.put("inputs",List.of("INVALID"));assertCreateStatus("AMP_ACOUSTIC",values,400);
    }

    @Test
    void skuFieldsDifferByCategoryAndCannotAcceptIrrelevantHandStyle() throws Exception {
        long guitar=create("ACOUSTIC_FOLK",acoustic());
        createSku(guitar,Map.of("color","日落色","hand_style","左手","packaging","单把"),200);
        assertEquals("左手",detail(guitar).path("skus").get(0).path("attributes").path("hand_style").asText());
        long strings=create("STRING_ACOUSTIC",Map.of("instrument","民谣吉他","material","磷青铜","string_count",6));
        JsonNode template=template(category("STRING_ACOUSTIC"));
        assertFalse(codes(template.path("sku")).contains("hand_style"));
        createSku(strings,Map.of("gauge",".012–.053","packaging","六根装"),200);
        createSku(strings,Map.of("gauge",".012–.053","packaging","六根装","hand_style","右手"),400);
        assertEquals(1,detail(strings).path("skus").size());
    }

    @Test
    void categoryChangeClearsIncompatibleModelAndSkuAttributes() throws Exception {
        long id=create("ACOUSTIC_FOLK",acoustic());
        createSku(id,Map.of("color","黑色","hand_style","右手","packaging","单件"),200);
        Map<String,Object> request=json.convertValue(detail(id),Map.class);
        request.put("categoryId",category("AMP_ELECTRIC"));
        request.put("attributes",Map.of("instrument","电吉他","rated_power",20));
        mvc.perform(put("/api/products/{id}",id).contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk());
        JsonNode after=detail(id);
        assertFalse(after.path("attributes").has("body_shape"));
        assertEquals(20,after.path("attributes").path("rated_power").asInt());
        assertFalse(after.path("skus").get(0).path("attributes").has("hand_style"));
        assertEquals("黑色",after.path("skus").get(0).path("attributes").path("color").asText());
    }

    @Test
    @WithMockUser(authorities="product:list")
    void readOnlyUsersSeeTemplatesAndKnownMigrationDoesNotInventPickupBrand() throws Exception {
        template(category("ACOUSTIC_FOLK"));
        long id=jdbc.queryForObject("SELECT id FROM crm_product WHERE model_code='XM-A200E'",Long.class);
        JsonNode product=detail(id);
        assertEquals("电箱",product.path("attributes").path("electronics").asText());
        assertFalse(product.path("attributes").has("pickup_brand"));
        assertFalse(product.path("missingAttributes").isEmpty());
        assertTrue(product.path("specifications").asText().contains("内置拾音系统"));
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(request("ACOUSTIC_FOLK",acoustic())))).andExpect(status().isForbidden());
    }

    private Map<String,Object> acoustic(){return new HashMap<>(Map.of("body_shape","D","body_size",41,"cutaway","无缺角","top_construction","单板","back_construction","合板","side_construction","合板","top_material","云杉","electronics","纯原声"));}
    private Map<String,Object> request(String category,Map<String,Object> attributes){return new HashMap<>(Map.of("categoryId",category(category),"modelCode",code(),"productName","结构属性测试产品","brand","弦木乐器","supplyType",1,"unit","把","attributes",attributes));}
    private long create(String category,Map<String,Object> attributes) throws Exception {
        String response=mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(request(category,attributes)))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).path("data").asLong();
    }
    private void assertCreateStatus(String category,Map<String,Object> attributes,int status) throws Exception {
        mvc.perform(post("/api/products").contentType("application/json").content(json.writeValueAsString(request(category,attributes)))).andExpect(status().is(status));
    }
    private void createSku(long id,Map<String,Object> attributes,int status) throws Exception {
        mvc.perform(post("/api/products/{id}/skus",id).contentType("application/json").content(json.writeValueAsString(Map.of("skuCode",code(),"attributes",attributes)))).andExpect(status().is(status));
    }
    private JsonNode detail(long id) throws Exception {
        String response=mvc.perform(get("/api/products/{id}",id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).path("data");
    }
    private JsonNode template(long id) throws Exception {
        String response=mvc.perform(get("/api/products/categories/{id}/attributes",id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).path("data");
    }
    private Set<String> codes(JsonNode fields){Set<String> result=new HashSet<>();fields.forEach(f->result.add(f.path("attributeCode").asText()));return result;}
    private long category(String code){return jdbc.queryForObject("SELECT id FROM crm_product_category WHERE category_code=?",Long.class,code);}
    private String code(){return "T_"+UUID.randomUUID().toString().replace("-","").substring(0,20).toUpperCase();}
}
