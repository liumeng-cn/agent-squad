import { describe, expect, it } from 'vitest'
import { parseCurlCommand } from './curlParser'

describe('parseCurlCommand', () => {
  it('parses POST curl with JSON body and infers template parameters', () => {
    const parsed = parseCurlCommand(
      `curl -X POST 'https://api.example.com/v1/orders/search' -H 'Authorization: Bearer {{credential}}' -H 'Content-Type: application/json' -d '{"order_id":"ORD001","limit":10}'`,
    )
    expect(parsed.method).toBe('POST')
    expect(parsed.url).toBe('https://api.example.com/v1/orders/search')
    expect(parsed.headers).toEqual([
      { name: 'Authorization', value: 'Bearer {{credential}}' },
      { name: 'Content-Type', value: 'application/json' },
    ])
    expect(parsed.bodyTemplate).toContain('{{order_id}}')
    expect(parsed.inferredParams.map(p => ({ name: p.name, type: p.type }))).toEqual([
      { name: 'order_id', type: 'string' },
      { name: 'limit', type: 'integer' },
    ])
  })

  it('extracts existing URL placeholders for GET curl', () => {
    const parsed = parseCurlCommand(`curl "https://api.example.com/users/{{user_id}}/profile"`)
    expect(parsed.method).toBe('GET')
    expect(parsed.bodyTemplate).toBeNull()
    expect(parsed.inferredParams).toHaveLength(1)
    expect(parsed.inferredParams[0]?.name).toBe('user_id')
  })

  it('rejects non-curl commands', () => {
    expect(() => parseCurlCommand('wget https://example.com')).toThrow('curl')
  })
})
