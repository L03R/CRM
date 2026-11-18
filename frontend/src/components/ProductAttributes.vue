<template>
  <div>
    <el-descriptions v-if="readOnly && visibleFields.length" :column="2" border>
      <el-descriptions-item v-for="field in visibleFields" :key="field.attributeCode" :label="field.attributeName">
        {{ attributeText(field, modelValue) }}
      </el-descriptions-item>
    </el-descriptions>
    <template v-else-if="!readOnly">
      <el-form-item v-for="field in visibleFields" :key="field.attributeCode" :label="field.attributeName"
        :prop="['attributes', field.attributeCode]" :rules="field.required ? [{ required: true, type: field.inputType === 'NUMBER' ? 'number' : field.inputType === 'MULTI' ? 'array' : 'string', message: '请填写' + field.attributeName }] : []">
        <el-input-number v-if="field.inputType === 'NUMBER'" :model-value="modelValue[field.attributeCode]"
          :precision="field.decimalPlaces" :min="0" :max="1000000000" @update:model-value="update(field, $event)" />
        <el-select v-else-if="field.inputType === 'MULTI'" :model-value="modelValue[field.attributeCode] || []" multiple clearable filterable style="width:100%" @update:model-value="update(field, $event)">
          <el-option v-for="option in field.options" :key="option" :label="option" :value="option" />
        </el-select>
        <el-select v-else-if="field.inputType === 'SELECT' || field.options.length" :model-value="modelValue[field.attributeCode]"
          :allow-create="field.inputType === 'TEXT'" filterable default-first-option clearable style="width:100%" @update:model-value="update(field, $event)">
          <el-option v-for="option in field.options" :key="option" :label="option" :value="option" />
        </el-select>
        <el-input v-else :model-value="modelValue[field.attributeCode]" maxlength="200" @update:model-value="update(field, $event)" />
        <span v-if="field.unit" class="attribute-unit">{{ field.unit }}</span>
      </el-form-item>
    </template>
    <p v-if="!fields.length" class="text-sub">此分类尚未配置属性模板，可先填写补充说明。</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { attributeVisible, attributeText } from '@/utils/productAttributes'

const props = defineProps({ fields: { type: Array, default: () => [] }, modelValue: { type: Object, default: () => ({}) }, readOnly: Boolean })
const emit = defineEmits(['update:modelValue'])
const visibleFields = computed(() => props.fields.filter(field => attributeVisible(field, props.modelValue)))

function update(field, value) {
  const values = { ...props.modelValue, [field.attributeCode]: value }
  for (const item of props.fields) if (!attributeVisible(item, values)) delete values[item.attributeCode]
  emit('update:modelValue', values)
}
</script>

<style scoped>
.attribute-unit { margin-left: 8px; color: #98a2b3; }
</style>
