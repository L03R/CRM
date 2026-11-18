export const attributeVisible = (field, values) => !field.conditionCode || values[field.conditionCode] === field.conditionValue

export function compatibleAttributes(fields, values = {}) {
  const result = {}
  for (const field of fields) {
    const value = values[field.attributeCode]
    if (value == null || value === '') continue
    const valid = field.inputType === 'NUMBER' ? typeof value === 'number'
      : field.inputType === 'MULTI' ? Array.isArray(value) && value.every(v => field.options.includes(v))
      : field.inputType === 'SELECT' ? field.options.includes(value) : typeof value === 'string'
    if (valid) result[field.attributeCode] = value
  }
  for (const field of fields) if (!attributeVisible(field, result)) delete result[field.attributeCode]
  return result
}

export function attributeText(field, values = {}) {
  const value = values[field.attributeCode]
  if (value == null || value === '' || Array.isArray(value) && !value.length) return field.required ? '待完善' : '—'
  return (Array.isArray(value) ? value.join('、') : value) + (field.unit ? ' ' + field.unit : '')
}

export function constructionSummary(values = {}) {
  const structure = [values.top_construction, values.back_construction, values.side_construction]
  if (structure.some(v => !v)) return '待完善'
  return { '单板/单板/单板': '全单', '单板/单板/合板': '面背单', '单板/合板/合板': '面单', '合板/合板/合板': '合板' }[structure.join('/')] || '其他组合'
}
