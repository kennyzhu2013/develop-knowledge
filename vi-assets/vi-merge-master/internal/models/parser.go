package models

import (
	"encoding/json"
	"fmt"
)

// ------------------- 聊天业务解析器 -------------------
//type ChatParser struct{}
//
//func NewChatParser() *ChatParser {
//	return &ChatParser{}
//}
//
//func (p *ChatParser) GetBusinessType() string {
//	return BusinessTypeChat
//}
//
//func (p *ChatParser) ParseContent(pType string, msgType string, content []byte) (interface{}, error) {
//	//
//	switch pType {
//	case "send":
//		var data ChatSendContent
//		if err := json.Unmarshal(content, &data); err != nil {
//			return nil, fmt.Errorf("chat send parse failed: %w", err)
//		}
//		return data, nil
//	case "heartbeat":
//		var data ChatHeartbeatContent
//		if err := json.Unmarshal(content, &data); err != nil {
//			return nil, fmt.Errorf("chat heartbeat parse failed: %w", err)
//		}
//		return data, nil
//	default:
//		return nil, fmt.Errorf("chat parser: unknown msg type %s", msgType)
//	}
//}

// --------------start of MergeParser --------------------
type MergeParser struct{}

func NewMergeParser() *MergeParser {
	return &MergeParser{}
}

func (p *MergeParser) GetBusinessType() string {
	return BusinessTypeMerge
}

func (p *MergeParser) ParseContent(pType string, msgType string, content []byte) (interface{}, error) {
	data := &MergeContent{
		SubType: msgType,
	}

	switch msgType {
	case WsTextMessage:
		if err := json.Unmarshal(content, data); err != nil {
			return nil, fmt.Errorf("merge text parse failed: %w", err)
		}
		return data, nil
	case WsBinaryMessage:
		//数据太小当控制事件
		if len(content) < 10 {
			data.Pcm = content
			return data, nil
		}
		source := content[0]
		mediaByte := content[1:]
		data.Source = uint(source)
		data.Pcm = mediaByte
		return data, nil
	default:
		return nil, fmt.Errorf("merge parser: unknown msg type %s", msgType)
	}
}

//--------------end of MergeParser --------------------

// ------------------- 解析器管理器 -------------------
type ParserManager struct {
	parsers map[string]MessageParser // 业务类型 -> 解析器
}

var globalParserManager = &ParserManager{
	parsers: make(map[string]MessageParser),
}

func InitParserManager() {
	// 注册所有解析器
	//globalParserManager.RegisterParser(NewChatParser())
	globalParserManager.RegisterParser(NewMergeParser())
}

func (m *ParserManager) RegisterParser(p MessageParser) {
	m.parsers[p.GetBusinessType()] = p
}

func (m *ParserManager) GetParser(bizType string) (MessageParser, error) {
	p, ok := m.parsers[bizType]
	if !ok {
		return nil, fmt.Errorf("no parser for business type %s", bizType)
	}
	return p, nil
}

// GetGlobalParserManager 获取全局解析器管理器
func GetGlobalParserManager() *ParserManager {
	return globalParserManager
}
