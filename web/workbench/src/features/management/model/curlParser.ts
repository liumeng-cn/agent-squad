import type { HttpMethod, KeyValue } from './agentConfig'
import type { ParamField } from './toolSchema'
import { bodyMethods, httpMethods } from './agentConfigLabels'

export interface ParsedCurl {
  method: HttpMethod
  url: string
  headers: KeyValue[]
  bodyTemplate: string | null
  inferredParams: ParamField[]
}

/**
 * 解析常见 cURL 命令，提取请求方法、地址、请求头与请求体模板，降低手填 HTTP 工具门槛。
 */
export function parseCurlCommand(raw: string): ParsedCurl {
  const normalized = raw.replace(/\\\r?\n/g, ' ').trim()
  if (!normalized) {
    throw new Error('请粘贴有效的 curl 命令')
  }

  const tokens = tokenizeShell(normalized)
  if (!tokens.length || tokens[0]?.toLowerCase() !== 'curl') {
    throw new Error('命令需以 curl 开头')
  }

  let explicitMethod: HttpMethod | null = null
  let url = ''
  const headers: KeyValue[] = []
  const dataParts: string[] = []

  for (let i = 1; i < tokens.length; i++) {
    const token = tokens[i]!
    if ((token === '-X' || token === '--request') && tokens[i + 1]) {
      const upper = tokens[++i]!.toUpperCase() as HttpMethod
      if (httpMethods.includes(upper)) explicitMethod = upper
      continue
    }
    if ((token === '-H' || token === '--header') && tokens[i + 1]) {
      const headerLine = tokens[++i]!
      const colonIdx = headerLine.indexOf(':')
      if (colonIdx > 0) {
        const name = headerLine.slice(0, colonIdx).trim()
        const value = headerLine.slice(colonIdx + 1).trim()
        if (name) headers.push({ name, value })
      }
      continue
    }
    if (
      (token === '-d' ||
        token === '--data' ||
        token === '--data-raw' ||
        token === '--data-binary' ||
        token === '--json') &&
      tokens[i + 1]
    ) {
      const payload = tokens[++i]!
      dataParts.push(payload)
      if (token === '--json' && !headers.some(h => h.name.toLowerCase() === 'content-type')) {
        headers.push({ name: 'Content-Type', value: 'application/json' })
      }
      continue
    }
    if ((token === '--url') && tokens[i + 1]) {
      url = tokens[++i]!
      continue
    }
    if (!token.startsWith('-') && !url && /^https?:\/\//i.test(token)) {
      url = token
    }
  }

  if (!url) {
    throw new Error('未在命令中识别出 http/https 请求地址')
  }

  const method: HttpMethod = explicitMethod ?? (dataParts.length ? 'POST' : 'GET')
  const rawBody = dataParts.length ? dataParts.join('&') : null
  let bodyTemplate: string | null = bodyMethods.includes(method) ? rawBody : null

  const inferredParams: ParamField[] = []
  const seenNames = new Set<string>()

  // 1. 提取 URL 或 Body 中已有的 {{param}} 占位符
  for (const match of `${url} ${bodyTemplate ?? ''}`.matchAll(/\{\{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*\}\}/g)) {
    const paramName = match[1]!
    if (!seenNames.has(paramName)) {
      seenNames.add(paramName)
      inferredParams.push({
        name: paramName,
        type: 'string',
        required: true,
        description: '',
        enumValues: '',
      })
    }
  }

  // 2. 若请求体是扁平 JSON 且尚未含占位符，自动转换成 {{key}} 模板并推导参数
  if (bodyTemplate && inferredParams.length === 0) {
    try {
      const parsed = JSON.parse(bodyTemplate) as unknown
      if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
        const templatedObj: Record<string, unknown> = {}
        for (const [key, val] of Object.entries(parsed as Record<string, unknown>)) {
          if (/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(key) && (typeof val === 'string' || typeof val === 'number' || typeof val === 'boolean')) {
            templatedObj[key] = `{{${key}}}`
            if (!seenNames.has(key)) {
              seenNames.add(key)
              inferredParams.push({
                name: key,
                type: typeof val === 'number' ? (Number.isInteger(val) ? 'integer' : 'number') : typeof val === 'boolean' ? 'boolean' : 'string',
                required: true,
                description: '',
                enumValues: '',
              })
            }
          } else {
            templatedObj[key] = val
          }
        }
        bodyTemplate = JSON.stringify(templatedObj, null, 2)
      }
    } catch {
      // 非 JSON 请求体保持原样
    }
  }

  return { method, url, headers, bodyTemplate, inferredParams }
}

function tokenizeShell(input: string): string[] {
  const result: string[] = []
  let current = ''
  let quote: "'" | '"' | null = null

  for (let i = 0; i < input.length; i++) {
    const ch = input[i]!
    if (quote) {
      if (ch === '\\' && quote === '"' && i + 1 < input.length) {
        current += input[++i]!
      } else if (ch === quote) {
        quote = null
      } else {
        current += ch
      }
      continue
    }

    if (ch === "'" || ch === '"') {
      quote = ch
    } else if (/\s/.test(ch)) {
      if (current) {
        result.push(current)
        current = ''
      }
    } else if (ch === '\\' && i + 1 < input.length) {
      current += input[++i]!
    } else {
      current += ch
    }
  }
  if (current) result.push(current)
  return result
}
