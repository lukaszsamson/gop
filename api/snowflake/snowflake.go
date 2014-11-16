package snowflake

import (
	"time"
)

var (
	instance  uint8
	counter   uint8
	lastUsed  uint64
	result    chan uint64
	request   chan int
	setupDone bool
)

func Setup(inst uint8) {
	instance = inst
	counter = 0
	lastUsed = 0
	if setupDone {
		return
	}
	result = make(chan uint64)
	request = make(chan int)
	go worker(request, result)
	setupDone = true
}

var getTimeNano = func() uint64 {
	return uint64(time.Now().UnixNano())
}

var waitForTimeChange = func() {
	<-time.After((1 << 15) * time.Nanosecond)
}

func generateIdImpl() uint64 {
	t := getTimeNano()
	t1 := (t >> 8) & ^uint64(0xff)
	if lastUsed != t1 {
		lastUsed = t1
		counter = 0
	} else {
		if counter == 0xFF {
			waitForTimeChange()
			return generateIdImpl()
		}
		counter = counter + 1
	}
	id := (uint64(instance) << 56) | t1 | uint64(counter)
	return id
}

var generateId = func() uint64 {
	return generateIdImpl()
}

func worker(get <-chan int, res chan<- uint64) {
	for {
		<-get
		id := generateId()
		res <- id
	}
}

func GetId() uint64 {
	request <- 1
	return <-result
}
