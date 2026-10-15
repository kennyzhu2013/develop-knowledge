package dtmf

import "errors"

type SlideBuffer struct {
	// 数据缓冲区
	buf []byte
	// 缓冲区写入位置
	writeIndex int
}

func NewSlideBuffer(size int) *SlideBuffer {
	return &SlideBuffer{buf: make([]byte, size)}
}

func (b *SlideBuffer) Write(data []byte) (int, error) {
	expect := b.writeIndex + len(data)
	// 确定是否扩容
	if !b.ensureCapacity(expect) {
		// 采用慢增长方式
		// 每次增加bufLen/4，直到达到期望的容量
		b.growSlow(expect)
	}

	count := copy(b.buf[b.writeIndex:], data)
	b.writeIndex += count
	return count, nil
}

func (b *SlideBuffer) Read(n int) ([]byte, error) {
	return b.ReadCopy(n, false)
}

func (b *SlideBuffer) ReadCopy(n int, bCopy bool) ([]byte, error) {
	if n <= 0 {
		return nil, nil
	}
	if n > b.writeIndex {
		return nil, errors.New("no enough data for read")
	}
	if bCopy {
		// 目标数据拷贝
		buf := make([]byte, n)
		copy(buf, b.buf[:n])
		// 从缓冲区中删除目标数据
		copy(b.buf, b.buf[n:])
		b.writeIndex -= n
		return buf, nil
	}
	return b.buf[:n], nil
}

func (b *SlideBuffer) Cut(n int) (int, error) {
	if n > b.writeIndex {
		n = b.writeIndex
	}
	copy(b.buf, b.buf[n:])
	b.writeIndex -= n
	return n, nil
}

func (b *SlideBuffer) Reset() {
	b.buf = b.buf[:0]
	b.writeIndex = 0
}

func (b *SlideBuffer) Len() int {
	return b.writeIndex
}

func (b *SlideBuffer) ensureCapacity(expect int) bool {
	return expect <= cap(b.buf)
}

func (b *SlideBuffer) growSlow(expect int) {
	bufCap := cap(b.buf)
	newCap := bufCap + (bufCap >> 2)
	for newCap < expect {
		newCap += bufCap >> 2
	}
	buf := make([]byte, newCap)
	copy(buf, b.buf)
	b.buf = buf
}