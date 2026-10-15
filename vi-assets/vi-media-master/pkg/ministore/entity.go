package ministore

import "encoding/binary"

const entityHeaderSize = 10

const (
	PUT uint16 = iota
	DEL
)

// Entity 写入文件的记录
type Entity struct {
	Key       []byte
	Value     []byte
	KeySize   uint32
	ValueSize uint32
	// 标记是否需要索引中删除
	Mark uint16
}

func NewEntity(key, value []byte, mark uint16) *Entity {
	return &Entity{
		Key:       key,
		Value:     value,
		KeySize:   uint32(len(key)),
		ValueSize: uint32(len(value)),
		Mark:      mark,
	}
}

func (e *Entity) GetSize() int64 {
	return int64(entityHeaderSize + e.KeySize + e.ValueSize)
}

// Encode 编码 Entry，返回字节数组
func (e *Entity) Encode() ([]byte, error) {
	buf := make([]byte, e.GetSize())
	binary.BigEndian.PutUint32(buf[0:4], e.KeySize)
	binary.BigEndian.PutUint32(buf[4:8], e.ValueSize)
	binary.BigEndian.PutUint16(buf[8:10], e.Mark)
	copy(buf[entityHeaderSize:entityHeaderSize+e.KeySize], e.Key)
	copy(buf[entityHeaderSize+e.KeySize:], e.Value)
	return buf, nil
}

// Decode 解码 buf 字节数组，返回 Entry
func Decode(buf []byte) (*Entity, error) {
	// 头部十个直接先读出.
	ks := binary.BigEndian.Uint32(buf[0:4])
	vs := binary.BigEndian.Uint32(buf[4:8])
	mark := binary.BigEndian.Uint16(buf[8:10])
	return &Entity{KeySize: ks, ValueSize: vs, Mark: mark}, nil
}