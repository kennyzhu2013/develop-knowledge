package rtp

import (
	"context"
	"golang.org/x/sys/unix"
	"net"
	"syscall"
)

func Control(network, address string, c syscall.RawConn) (err error) {
	_ = c.Control(func(fd uintptr) {
		if err = unix.SetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_REUSEADDR, 1); err != nil {
			return
		}
		if err = unix.SetsockoptInt(int(fd), unix.SOL_SOCKET, unix.SO_REUSEPORT, 1); err != nil {
			return
		}
	})
	return nil
}

func ResolveAddr(network, address string) (net.Addr, error) {
	switch network {
	case "ip", "ip4", "ip6":
		return net.ResolveIPAddr(network, address)
	case "tcp", "tcp4", "tcp6":
		return net.ResolveTCPAddr(network, address)
	case "udp", "udp4", "udp6":
		return net.ResolveUDPAddr(network, address)
	case "unix", "unixgram", "unixpacket":
		return net.ResolveUnixAddr(network, address)
	default:
		return nil, net.UnknownNetworkError(network)
	}
}

func Dial(network, src, dst string) (net.Conn, error) {
	addr, err := ResolveAddr(network, src)
	if err != nil {
		return nil, err
	}
	d := net.Dialer{
		Control:   Control,
		LocalAddr: addr,
	}
	return d.Dial(network, dst)
}

var (
	listenConfig = net.ListenConfig{
		Control: Control,
	}
)

func ListenUDP(addr string) (*net.UDPConn, error) {
	conn, err := listenConfig.ListenPacket(context.Background(), "udp", addr)
	if err != nil {
		return nil, err
	}
	return conn.(*net.UDPConn), nil
}