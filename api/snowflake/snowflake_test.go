package snowflake

import (
	"testing"
	"time"
)

func TestMutex(t *testing.T) {
	Setup(0x00)
	working := false

	generateIdOrg := generateId

	generateId = func() uint64 {
		if working {
			t.Error("Mutex doesnt work")
		}
		working = true
		<-time.After(200 * time.Millisecond)
		working = false
		return 1
	}
	r := make(chan int)
	go func() {
		GetId()
		r <- 1
	}()
	go func() {
		GetId()
		r <- 1
	}()

	<-r
	<-r

	generateId = generateIdOrg
}

func TestCounterIncrease(t *testing.T) {
	Setup(0x00)
	lastUsed = 100
	getTimeNanoOrig := getTimeNano
	getTimeNano = func() uint64 {
		return 0
	}
	generateId()
	id2 := generateId()
	if id2 != 0x0000000000000001 {
		t.Errorf("0x0000000000000001 expected, got %x", id2)
	}
	getTimeNano = getTimeNanoOrig
}

func TestWait(t *testing.T) {
	Setup(0x00)
	lastUsed = 100

	getTimeNanoOrig := getTimeNano
	i := uint64(1)
	getTimeNano = func() uint64 {
		return i << 16
	}
	id1 := generateId()
	if id1 != 0x0000000000000100 {
		t.Errorf("0x0000000000000100 expected, got %x", id1)
	}
	counter = 0xff
	waitForTimeChangeOrig := waitForTimeChange
	waitForTimeChange = func() {
		i = i + 1
	}

	id2 := generateId()
	if id2 != 0x0000000000000200 {
		t.Errorf("0x0000000000000200 expected, got %x", id2)
	}
	getTimeNano = getTimeNanoOrig
	waitForTimeChange = waitForTimeChangeOrig
}

func TestCounterReset(t *testing.T) {
	Setup(0x00)
	lastUsed = 100
	getTimeNanoOrig := getTimeNano

	i := uint64(0)
	getTimeNano = func() uint64 {
		i = i + 1
		return i << 16
	}
	id1 := generateId()

	if id1 != 0x0000000000000100 {
		t.Errorf("0x0000000000000100 expected, got %x", id1)
	}

	id2 := generateId()
	if id2 != 0x0000000000000200 {
		t.Errorf("0x0000000000000200 expected, got %x", id2)
	}
	getTimeNano = getTimeNanoOrig
}

func TestSetup(t *testing.T) {
	Setup(0xbe)
	lastUsed = 100
	getTimeNanoOrig := getTimeNano
	getTimeNano = func() uint64 {
		return 0
	}
	id := generateId()
	if id != 0xbe00000000000000 {
		t.Errorf("0xbe00000000000000 expected, got %x", id)
	}
	getTimeNano = getTimeNanoOrig
}

func TestTime(t *testing.T) {
	Setup(0x00)
	lastUsed = 100
	getTimeNanoOrig := getTimeNano
	getTimeNano = func() uint64 {
		return 0x1234567890abcdef
	}
	id := generateId()
	if id != 0x001234567890ab00 {
		t.Errorf("0x001234567890ab00 expected, got %x", id)
	}
	getTimeNano = getTimeNanoOrig
}
