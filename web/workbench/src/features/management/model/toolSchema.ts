import type { JsonSchema } from './agentConfig'

/**
 * 工具参数定义与 JSON Schema 互转。
 * HTTP 工具在页面用参数表格维护参数，保存时生成 inputSchema；内置与 MCP 工具的 Schema 来自实现，只读展示。
 */
export type ParamType = 'string' | 'number' | 'integer' | 'boolean' | 'array' | 'object'

export const paramTypes: ParamType[] = ['string', 'number', 'integer', 'boolean', 'array', 'object']

export const paramTypeLabels: Record<ParamType, string> = {
  string: '文本',
  number: '数字',
  integer: '整数',
  boolean: '布尔',
  array: '文本列表',
  object: '对象',
}

/** 参数表格的一行 */
export interface ParamField {
  name: string
  type: ParamType
  required: boolean
  description: string
  /** 逗号分隔的可选值，仅文本、数字和整数类型生效 */
  enumValues: string
}

export const PARAM_NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/
export const MAX_PARAMS = 64
/** 请求模板中的凭据占位符名称，不能作为参数名 */
export const CREDENTIAL_PLACEHOLDER = 'credential'

const ENUM_TYPES: ParamType[] = ['string', 'number', 'integer']
const PLACEHOLDER_PATTERN = /\{\{([A-Za-z_][A-Za-z0-9_]*)\}\}/g

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** JSON Schema 的 type 可能是数组（如 ["string", "null"]），取第一个可识别的类型，无法识别时按文本处理。 */
function toParamType(value: unknown): ParamType {
  const candidates = Array.isArray(value) ? value : [value]
  for (const candidate of candidates) {
    const known = paramTypes.find(type => type === candidate)
    if (known) {
      return known
    }
  }
  return 'string'
}

export function emptyObjectSchema(): JsonSchema {
  return { type: 'object', properties: {} }
}

export function emptyParamField(): ParamField {
  return { name: '', type: 'string', required: false, description: '', enumValues: '' }
}

/** 读取 Schema 顶层属性生成参数表格行，保留属性声明顺序。 */
export function fieldsFromSchema(schema: JsonSchema | null | undefined): ParamField[] {
  if (!isRecord(schema) || !isRecord(schema.properties)) {
    return []
  }
  const required = Array.isArray(schema.required)
    ? schema.required.filter((item): item is string => typeof item === 'string')
    : []

  const fields: ParamField[] = []
  for (const [name, definition] of Object.entries(schema.properties)) {
    const property = isRecord(definition) ? definition : {}
    const enumList = Array.isArray(property.enum) ? property.enum : []
    fields.push({
      name,
      type: toParamType(property.type),
      required: required.includes(name),
      description: typeof property.description === 'string' ? property.description : '',
      enumValues: enumList.map(item => String(item)).join(', '),
    })
  }
  return fields
}

function splitEnumText(text: string): string[] {
  return text
    .split(/[,，]/)
    .map(item => item.trim())
    .filter(item => item !== '')
}

/** 解析可选值；数字类型只保留合法数字，整数类型只保留整数。 */
export function parseEnumValues(field: ParamField): (string | number)[] {
  if (!ENUM_TYPES.includes(field.type)) {
    return []
  }
  const items = splitEnumText(field.enumValues)
  if (field.type === 'string') {
    return items
  }

  const numbers: number[] = []
  for (const item of items) {
    const value = Number(item)
    const valid = Number.isFinite(value) && (field.type === 'number' || Number.isInteger(value))
    if (valid) {
      numbers.push(value)
    }
  }
  return numbers
}

/** 由参数表格生成顶层为 object 的 Schema；列表参数的元素固定为文本。 */
export function schemaFromFields(fields: ParamField[]): JsonSchema {
  const properties: Record<string, JsonSchema> = {}
  const required: string[] = []

  for (const field of fields) {
    const name = field.name.trim()
    if (!name) {
      continue
    }
    const property: JsonSchema = { type: field.type }
    const description = field.description.trim()
    if (description) {
      property.description = description
    }
    if (field.type === 'array') {
      property.items = { type: 'string' }
    }
    const enumValues = parseEnumValues(field)
    if (enumValues.length) {
      property.enum = enumValues
    }
    properties[name] = property
    if (field.required) {
      required.push(name)
    }
  }

  const schema: JsonSchema = { type: 'object', properties }
  if (required.length) {
    schema.required = required
  }
  return schema
}

/** 校验参数表格，返回面向用户的错误说明；为空表示通过。 */
export function validateFields(fields: ParamField[]): string[] {
  const errors: string[] = []
  if (fields.length > MAX_PARAMS) {
    errors.push(`参数不能超过 ${MAX_PARAMS} 个`)
  }

  const seen = new Set<string>()
  fields.forEach((field, index) => {
    const name = field.name.trim()
    const label = name || `第 ${index + 1} 个参数`
    if (!PARAM_NAME_PATTERN.test(name)) {
      errors.push(`${label}：名称须以字母或下划线开头，只含字母、数字和下划线`)
    } else if (name === CREDENTIAL_PLACEHOLDER) {
      errors.push(`${label}：credential 是凭据占位符，不能作为参数名`)
    } else if (seen.has(name)) {
      errors.push(`${label}：参数名称重复`)
    }
    seen.add(name)

    const declared = splitEnumText(field.enumValues).length
    if (declared && field.type !== 'string' && parseEnumValues(field).length !== declared) {
      errors.push(`${label}：可选值须为${field.type === 'integer' ? '整数' : '数字'}`)
    }
  })
  return errors
}

/** 提取模板中的 {{名称}} 占位符，按首次出现顺序去重。 */
export function placeholdersIn(...texts: (string | null | undefined)[]): string[] {
  const names = new Set<string>()
  for (const text of texts) {
    if (!text) {
      continue
    }
    for (const match of text.matchAll(PLACEHOLDER_PATTERN)) {
      names.add(match[1]!)
    }
  }
  return [...names]
}

/** 将请求体模板中的占位符替换为 null 后检查是否为合法 JSON，与服务端生成请求体的规则一致。 */
export function isValidBodyTemplate(template: string): boolean {
  const replaced = template.replace(PLACEHOLDER_PATTERN, 'null')
  try {
    JSON.parse(replaced)
    return true
  } catch {
    return false
  }
}

/** 试运行表单中单个参数的输入值：布尔参数为勾选状态，其余为输入文本。 */
export type TestInputValue = string | boolean

export function initialTestValues(fields: ParamField[]): Record<string, TestInputValue> {
  const values: Record<string, TestInputValue> = {}
  for (const field of fields) {
    values[field.name] = field.type === 'boolean' ? false : ''
  }
  return values
}

export interface TestArguments {
  arguments: Record<string, unknown>
  errors: string[]
}

/** 将试运行表单输入转换为工具参数；空的可选参数不传，必填参数为空时报错。 */
export function buildTestArguments(fields: ParamField[], values: Record<string, TestInputValue>): TestArguments {
  const result: Record<string, unknown> = {}
  const errors: string[] = []

  for (const field of fields) {
    const raw = values[field.name]
    if (field.type === 'boolean') {
      result[field.name] = raw === true
      continue
    }

    const text = typeof raw === 'string' ? raw.trim() : ''
    if (!text) {
      if (field.required) {
        errors.push(`${field.name}：必填参数未填写`)
      }
      continue
    }

    const converted = convertTestValue(field, text)
    if (converted.error) {
      errors.push(`${field.name}：${converted.error}`)
    } else {
      result[field.name] = converted.value
    }
  }
  return { arguments: result, errors }
}

function convertTestValue(field: ParamField, text: string): { value?: unknown; error?: string } {
  if (field.type === 'string') {
    return { value: text }
  }
  if (field.type === 'number' || field.type === 'integer') {
    const value = Number(text)
    const valid = Number.isFinite(value) && (field.type === 'number' || Number.isInteger(value))
    return valid ? { value } : { error: field.type === 'integer' ? '请输入整数' : '请输入数字' }
  }

  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    return { error: '请输入合法的 JSON' }
  }
  if (field.type === 'array' && !Array.isArray(parsed)) {
    return { error: '请输入 JSON 数组，例如 ["a", "b"]' }
  }
  if (field.type === 'object' && !isRecord(parsed)) {
    return { error: '请输入 JSON 对象，例如 {"key": "value"}' }
  }
  return { value: parsed }
}
