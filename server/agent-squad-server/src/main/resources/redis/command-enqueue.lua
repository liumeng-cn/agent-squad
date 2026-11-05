-- 仅登记可信定位引用。业务命令和原生日志必须已持久提交。
-- KEYS: 同一分区 hash tag 下的绑定状态、通道 Stream。
-- ARGV: 容量、schemaVersion、kind、userId、bindingId、commandId、traceparent。
local queued = redis.call('HGET', KEYS[1], 'queued')
if not queued and redis.call('XLEN', KEYS[2]) >= tonumber(ARGV[1]) then
    return redis.error_reply('WAKEUP_CAPACITY')
end
local version = redis.call('HINCRBY', KEYS[1], 'dirtyVersion', 1)
if not queued then
    queued = redis.call('XADD', KEYS[2], '*',
        'schemaVersion', ARGV[2], 'kind', ARGV[3], 'userId', ARGV[4],
        'bindingId', ARGV[5], 'commandId', ARGV[6], 'traceparent', ARGV[7],
        'sessionId', ARGV[8] or '', 'mappingVersion', ARGV[9] or '')
    redis.call('HSET', KEYS[1], 'queued', queued)
end
return {tostring(version), queued}
