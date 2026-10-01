-- Deletes KEYS[1] only if its current value still equals ARGV[1] (this attempt's own
-- "PROCESSING:<token>" marker). A plain DEL could otherwise remove a newer claim taken out by another
-- consumer after ours expired.
if redis.call('get', KEYS[1]) == ARGV[1] then
    return redis.call('del', KEYS[1])
else
    return 0
end
