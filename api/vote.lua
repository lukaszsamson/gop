--KEYS
--1 questionsAnswered:%userId
--2 questions:%questionId:votes
--ARGV
--1 %questionId
--2 %pickId

local voted = redis.call("SISMEMBER", KEYS[1], ARGV[1])

if voted == 1 then
  return redis.error_reply("Already voted")
end

redis.call("HINCRBY", KEYS[2], ARGV[2], 1)
redis.call("SADD", KEYS[1], ARGV[1])
