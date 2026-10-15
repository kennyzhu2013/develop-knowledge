package proxy

import (
	"cmic.chinamobile.com/ziyan/go-sdk/process"
	"sync"
	"time"
	"vi-media/internal/pkg/log"
)

type SessionManager struct {
	sync.RWMutex
	pp     *PortPool
	store  []*Session
	active int
}

var once sync.Once
var selectKey int
var sessionManager *SessionManager

func initManager(start, capacity int) error {
	var err error
	once.Do(func() {
		var pp *PortPool
		pp, err = InitPortPool(start, capacity)
		if err != nil {
			return
		}
		sessionManager = &SessionManager{pp: pp, store: make([]*Session, capacity)}
		// Start session expiration scan
		go sessionManager.scan()
	})
	return err
}

func GetManager() *SessionManager {
	return sessionManager
}

func (m *SessionManager) GetFree(sessionId string) *Session {
	if len(sessionId) == 0 {
		return nil
	}
	m.Lock()
	defer m.Unlock()
	// 判断会话是否已存在
	for _, session := range m.store {
		if session != nil && session.Id() == sessionId {
			return nil
		}
	}
	// 获取新会话
	if selectKey >= len(m.store) {
		selectKey = 0
	}
	for i := selectKey; i < len(m.store); i++ {
		if m.store[i] == nil {
			port, _ := m.pp.Get()
			m.store[i] = newSession(sessionId, port)
			m.active++
			selectKey = i + 1
			return m.store[i]
		}
		if m.store[i].IsFree() {
			m.store[i].Reset(sessionId)
			m.active++
			selectKey = i + 1
			return m.store[i]
		}
	}
	for i := 0; i < selectKey; i++ {
		if m.store[i] == nil {
			port, _ := m.pp.Get()
			m.store[i] = newSession(sessionId, port)
			m.active++
			selectKey = i + 1
			return m.store[i]
		}
		if m.store[i].IsFree() {
			m.store[i].Reset(sessionId)
			m.active++
			selectKey = i + 1
			return m.store[i]
		}
	}
	return nil
}

func (m *SessionManager) Get(sessionId string) (*Session, bool) {
	if len(sessionId) == 0 {
		return nil, false
	}
	m.RLock()
	defer m.RUnlock()
	for _, session := range m.store {
		if session != nil && session.Id() == sessionId {
			return session, true
		}
	}
	return nil, false
}

func (m *SessionManager) Free(sessionId string) {
	if len(sessionId) == 0 {
		return
	}
	m.Lock()
	defer m.Unlock()
	for _, session := range m.store {
		if session != nil && session.Id() == sessionId {
			session.Free()
			m.active--
			return
		}
	}
}

func (m *SessionManager) Stop() {
	t := time.NewTicker(time.Second)
	for {
		select {
		case <-t.C:
			isAllFree := true
			for _, session := range m.store {
				if session != nil && !session.IsFree() {
					isAllFree = false
					break
				}
			}
			if isAllFree {
				return
			}
		}
	}
}

func (m *SessionManager) scan() {
	defer func() {
		if v := recover(); v != nil {
			log.WarnF("Recovered from sessionManager scan with %v, stack: %v", v, process.Stack())
		}
	}()
	t := time.NewTicker(time.Minute * 3)
	for {
		select {
		case <-t.C:
			m.RLock()
			size := 0
			for _, session := range m.store {
				if session != nil && !session.IsFree() {
					size++
				}
			}
			m.active = size
			m.RUnlock()

			for _, session := range m.store {
				if session == nil || session.IsFree() {
					continue
				}
				session.Scan()
			}
		}
	}
}

func (m *SessionManager) Size() int {
	return m.active
}