-- Reads the projection in one round trip.
-- KEYS[1-5] sets of active patients per level, KEYS[6] time of the latest event applied
-- Returns the size of every set, then that time in epoch microseconds ('' before any event).
local result = {}
for index = 1, 5 do
    result[index] = redis.call('SCARD', KEYS[index])
end
result[6] = redis.call('GET', KEYS[6]) or ''
return result
