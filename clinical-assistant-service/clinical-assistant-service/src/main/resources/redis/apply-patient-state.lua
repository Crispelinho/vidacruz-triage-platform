-- Applies the latest state of a patient to the emergency room projection, atomically.
-- KEYS[1] hash of the patient (level, status, occurredAt, sequence)
-- KEYS[2] time of the latest event applied
-- KEYS[3-7] set of active patients of each level
-- ARGV[1] patient id, ARGV[2] level code ('' when the event does not carry it), ARGV[3] status,
-- ARGV[4] occurredAt (epoch microseconds), ARGV[5] '1' when active, ARGV[6] retention seconds,
-- ARGV[7] per-patient sequence, ARGV[8-12] level codes in KEYS order.
-- Returns 1 when applied, 0 when the event is a duplicate or older than the stored state.
local storedSequence = tonumber(redis.call('HGET', KEYS[1], 'sequence') or '-1')
local sequence = tonumber(ARGV[7])
local storedAt = tonumber(redis.call('HGET', KEYS[1], 'occurredAt') or '-1')
local occurredAt = tonumber(ARGV[4])
if storedSequence > sequence or (storedSequence == sequence and storedAt >= occurredAt) then
    return 0
end

local level = ARGV[2]
if level == '' then
    level = redis.call('HGET', KEYS[1], 'level') or ''
    if level == '' then
        return 0
    end
end
local levelIndex = 0
for index = 8, 12 do
    if ARGV[index] == level then
        levelIndex = index - 7
    end
end
if levelIndex == 0 then
    return 0
end

for index = 3, 7 do
    redis.call('SREM', KEYS[index], ARGV[1])
end
redis.call('HSET', KEYS[1], 'level', level, 'status', ARGV[3], 'occurredAt', ARGV[4],
           'sequence', ARGV[7])
if ARGV[5] == '1' then
    redis.call('SADD', KEYS[2 + levelIndex], ARGV[1])
    redis.call('PERSIST', KEYS[1])
else
    -- Keep discharged patients for a while so a late, older event cannot bring them back.
    redis.call('EXPIRE', KEYS[1], ARGV[6])
end
local updated = redis.call('GET', KEYS[2])
if (not updated) or tonumber(updated) < occurredAt then
    redis.call('SET', KEYS[2], ARGV[4])
end
return 1
