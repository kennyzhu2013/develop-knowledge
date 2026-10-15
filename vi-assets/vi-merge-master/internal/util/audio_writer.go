package util

import (
	"bufio"
	"fmt"
	"os"
	"sync"
)

type PcmWriter struct {
	fd     *os.File
	bufW   *bufio.Writer
	mu     sync.Mutex
	closed bool
}

// NewPcmWriter 创建PCM追加写入器
// bufSize 缓冲区大小，建议 4096/8192/16384，匹配PCM帧大小
func NewPcmWriter(filePath string, bufSize int) (*PcmWriter, error) {
	// 打开文件：追加、不存在创建、只写，权限0644
	f, err := os.OpenFile(filePath, os.O_APPEND|os.O_CREATE|os.O_WRONLY, 0644)
	if err != nil {
		return nil, fmt.Errorf("open pcm file failed: %w", err)
	}

	w := bufio.NewWriterSize(f, bufSize)
	return &PcmWriter{
		fd:   f,
		bufW: w,
	}, nil
}

// AppendPCM 追加PCM二进制帧数据
func (p *PcmWriter) AppendPCM(pcmData []byte) error {
	p.mu.Lock()
	defer p.mu.Unlock()

	if p.closed {
		return fmt.Errorf("pcm writer already closed")
	}

	// 写入二进制PCM字节
	_, err := p.bufW.Write(pcmData)
	if err != nil {
		return fmt.Errorf("write pcm buffer failed: %w", err)
	}
	return nil
}

// Flush 强制刷新缓冲区到磁盘
func (p *PcmWriter) Flush() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed {
		return nil
	}
	return p.bufW.Flush()
}

// Close 关闭写入器，自动刷新缓冲
func (p *PcmWriter) Close() error {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.closed {
		return nil
	}
	p.closed = true

	// 先刷缓冲
	err1 := p.bufW.Flush()
	// 再关闭文件句柄
	err2 := p.fd.Close()

	if err1 != nil {
		return err1
	}
	return err2
}

func Test() {
	// 缓冲区 8192字节，适配音频帧
	writer, err := NewPcmWriter("output.pcm", 8192)
	if err != nil {
		panic(err)
	}
	defer writer.Close()

	// 模拟多段PCM音频数据
	pcmFrame1 := make([]byte, 320) // 16k 16bit 单声道一帧典型大小
	pcmFrame2 := make([]byte, 320)

	// 写入第一段PCM
	err = writer.AppendPCM(pcmFrame1)
	if err != nil {
		fmt.Println("append frame1 err:", err)
	}

	// 写入第二段PCM
	err = writer.AppendPCM(pcmFrame2)
	if err != nil {
		fmt.Println("append frame2 err:", err)
	}

	// 定时/批量手动刷新，防止断电丢数据
	_ = writer.Flush()
	fmt.Println("pcm数据追加完成")
}
