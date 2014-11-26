--KEYS
--1 pick:%id1:image
--2 pick:%id2:image
--3 questions
--4 questions:%questionId
--5 questions:%questionId:votes
--ARGV
--1 %id1
--2 %id2
--3 %questionId

local pick1Image = redis.call("GET", KEYS[1])
local pick2Image = redis.call("GET", KEYS[2])

if pick1Image == nil or pick2Image == nil then
  return redis.error_reply("Invalid pickId")
end

local questionId = ARGV[3]
redis.call("SADD", KEYS[3], questionId)
redis.call("HMSET", KEYS[4], "pick1Id", ARGV[1], "pick1Image", pick1Image, "pick2Id", ARGV[2], "pick2Image", pick2Image)
redis.call("HMSET", KEYS[5], ARGV[1], 0, ARGV[2], 0)
