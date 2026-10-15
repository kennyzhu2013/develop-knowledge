package conf

import (
	"errors"
	"strconv"

	"github.com/spf13/viper"
)

type Registry struct {
	ClusterName string   `yaml:"clusterName"`
	ServerName  string   `yaml:"serverName"`
	ServerId    string   `yaml:"serverId"`
	Address     []string `yaml:"address"`
}

type AppInfo struct {
	Host       string `yaml:"host"`
	Port       int    `yaml:"port"`
	Version    string `yaml:"version"`
	Registry   `yaml:"registry"`
	MaxSession int `yaml:"maxSession"`
}

type LogInfo struct {
	LogLevel    string `yaml:"logLevel"`
	LogPath     string `yaml:"logPath"`
	LogFile     string `yaml:"logFile"`
	LogMaxFiles int    `yaml:"logMaxFiles"`
}

type RocketMsg struct {
	Namespace   string `json:"namespace" yaml:"namespace" mapstructure:"namespace"`
	AccessKey   string `json:"accessKey" yaml:"accessKey" mapstructure:"accessKey"`
	SecretKey   string `json:"secretKey" yaml:"secretKey" mapstructure:"secretKey"`
	Topic       string `json:"topic" yaml:"topic" mapstructure:"topic"`
	Endpoint    string `json:"endpoint" yaml:"endpoint" mapstructure:"endpoint"`
	MaxAttempts int    `json:"maxAttempts" yaml:"maxAttempts" mapstructure:"maxAttempts"`
}

type Outbound struct {
	Cluster string `json:"cluster" yaml:"cluster" mapstructure:"cluster"`
	AppName string `json:"appName" yaml:"appName" mapstructure:"appName"`
	Uri     string `json:"uri" yaml:"uri" mapstructure:"uri"`
	Timeout int    `json:"timeout" yaml:"timeout" mapstructure:"timeout"`
}

type BufferIO struct {
	Buffer2DiskMinutes int `json:"buffer2DiskMinutes" yaml:"buffer2DiskMinutes" mapstructure:"buffer2DiskMinutes"`
	DiskRwMax          int `json:"diskRwMax" yaml:"diskRwMax" mapstructure:"diskRwMax"`
	MemUsedPercent     int `json:"memUsedPercent" yaml:"memUsedPercent" mapstructure:"memUsedPercent"`
	DecisionType       int `json:"decisionType" yaml:"decisionType" mapstructure:"decisionType"`
}

type Ffmpeg struct {
	StaticPath string `json:"staticPath" yaml:"staticPath" mapstructure:"staticPath"`
}

type BizSetting struct {
	FinalSuffixMp3 bool `json:"finalSuffixMp3" yaml:"finalSuffixMp3" mapstructure:"finalSuffixMp3"`
}

type AppConfig struct {
	AppInfo    `yaml:"appInfo" mapstructure:"appInfo"`
	LogInfo    `yaml:"logInfo" mapstructure:"logInfo"`
	RocketMsg  `yaml:"rocketMq"  mapstructure:"rocketMq"`
	Outbound   `yaml:"outbound" mapstructure:"outbound"`
	Ffmpeg     `yaml:"ffmpeg" mapstructure:"ffmpeg"`
	BufferIO   `yaml:"bufferIO" mapstructure:"bufferIO"`
	BizSetting `yaml:"bizSetting" mapstructure:"bizSetting"`

	HttpAddress      string
	InnerHttpAddress string
}

var App AppConfig

func Init(filePath string) error {
	if len(filePath) == 0 {
		return errors.New("conf file path is empty")
	}
	v := viper.New()
	v.SetConfigFile(filePath)
	if err := v.ReadInConfig(); err != nil {
		return err
	}
	if err := v.Unmarshal(&App); err != nil {
		return err
	}
	App.HttpAddress = App.Host + ":" + strconv.Itoa(App.Port)
	App.InnerHttpAddress = App.Host + ":" + strconv.Itoa(App.Port+1)
	//App.LogRtpDebug = App.LogLevel < 1
	return nil
}
