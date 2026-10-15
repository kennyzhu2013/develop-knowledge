package logger

import (
	"fmt"
	"io"
)

type Logger struct {
	w      Writer
	level  Level
	fields []string
	stack  bool
}

func New(out io.Writer) *Logger {
	w, err := Wrap(out)
	if err != nil {
		panic(err)
	}
	return &Logger{w: w, level: InfoLevel}
}

func (l *Logger) Fields(fields ...string) *Logger {
	l.fields = append(l.fields, fields...)
	return l
}

func (l *Logger) Level(level Level) *Logger {
	l.level = level
	return l
}

func (l *Logger) Async() *Logger {
	if fw, ok := l.w.(*FileWriter); ok {
		fw.Async()
	}
	return l
}

func (l *Logger) Stack() *Logger {
	l.stack = true
	return l
}

func (l *Logger) DebugF(format string, args ...interface{}) {
	if DebugLevel < l.level {
		return
	}
	l.msg(DebugLevel, "", fmt.Sprintf(format, args...))
}

func (l *Logger) InfoF(format string, args ...interface{}) {
	if InfoLevel < l.level {
		return
	}
	l.msg(InfoLevel, "", fmt.Sprintf(format, args...))
}

func (l *Logger) WarnF(format string, args ...interface{}) {
	if WarnLevel < l.level {
		return
	}
	l.msg(WarnLevel, "", fmt.Sprintf(format, args...))
}

func (l *Logger) ErrorF(format string, args ...interface{}) {
	if ErrorLevel < l.level {
		return
	}
	l.msg(ErrorLevel, "", fmt.Sprintf(format, args...))
}

func (l *Logger) FatalF(format string, args ...interface{}) {
	if FatalLevel < l.level {
		return
	}
	l.msg(FatalLevel, "", fmt.Sprintf(format, args...))
}

func (l *Logger) DebugK(key string, format string, args ...interface{}) {
	if DebugLevel < l.level {
		return
	}
	l.msg(DebugLevel, key, fmt.Sprintf(format, args...))
}

func (l *Logger) InfoK(key string, format string, args ...interface{}) {
	if InfoLevel < l.level {
		return
	}
	l.msg(InfoLevel, key, fmt.Sprintf(format, args...))
}

func (l *Logger) WarnK(key string, format string, args ...interface{}) {
	if WarnLevel < l.level {
		return
	}
	l.msg(WarnLevel, key, fmt.Sprintf(format, args...))
}

func (l *Logger) ErrorK(key string, format string, args ...interface{}) {
	if ErrorLevel < l.level {
		return
	}
	l.msg(ErrorLevel, key, fmt.Sprintf(format, args...))
}

func (l *Logger) FatalK(key string, format string, args ...interface{}) {
	if FatalLevel < l.level {
		return
	}
	l.msg(FatalLevel, key, fmt.Sprintf(format, args...))
}

func (l *Logger) SysInfo(format string, args ...interface{}) {
	l.msg(SysInfoLevel, "", fmt.Sprintf(format, args...))
}

func (l *Logger) SysError(format string, args ...interface{}) {
	l.msg(SysErrorLevel, "", fmt.Sprintf(format, args...))
}

func (l *Logger) msg(level Level, key string, msg string) {
	_, _ = l.w.Write(newEvent(level, msg).Fields(l.fields...).Key(key), level >= SysInfoLevel)
}