-- 只能在业务提交后调用。版本变化或有未读尾部时，原子续排再确认旧引用。
-- ARGV: group、messageId、observedVersion、hasMore、其余为引用字段。
local queued = redis.call('HGET', KEYS[1], 'queued')
if queued ~= ARGV[2] then
    -- 接管后的迟到确认不能清除新的就绪标记。
    redis.call('XACK', KEYS[2], ARGV[1], ARGV[2])
    redis.call('XDEL', KEYS[2], ARGV[2])
    return 'STALE'
end
local version = tonumber(redis.call('HGET', KEYS[1], 'dirtyVersion'))
local result = 'CLEARED'
if version > tonumber(ARGV[3]) or ARGV[4] == '1' then
    result = redis.call('XADD', KEYS[2], '*',
        'schemaVersion', ARGV[5], 'kind', ARGV[6], 'userId', ARGV[7],
        'bindingId', ARGV[8], 'commandId', ARGV[9], 'traceparent', ARGV[10])
    redis.call('HSET', KEYS[1], 'queued', result)
else
    redis.call('DEL', KEYS[1])
end
redis.call('XACK', KEYS[2], ARGV[1], ARGV[2])
redis.call('XDEL', KEYS[2], ARGV[2])
return result
