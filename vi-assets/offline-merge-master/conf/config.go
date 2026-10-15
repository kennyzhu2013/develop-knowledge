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
	Host     string `yaml:"host"`
	Port     int    `yaml:"port"`
	Version  string `yaml:"version"`
	Registry `yaml:"registry"`
}

type LogInfo struct {
	LogLevel    string `yaml:"logLevel"`
	LogPath     string `yaml:"logPath"`
	LogFile     string `yaml:"logFile"`
	LogMaxFiles int    `yaml:"logMaxFiles"`
}

type RocketMsg struct {
	Namespace              string `json:"namespace" yaml:"namespace" mapstructure:"namespace"`
	AccessKey              string `json:"accessKey" yaml:"accessKey" mapstructure:"accessKey"`
	SecretKey              string `json:"secretKey" yaml:"secretKey" mapstructure:"secretKey"`
	MergeTopic             string `json:"mergeTopic" yaml:"mergeTopic" mapstructure:"mergeTopic"`
	MergeGroup             string `json:"mergeGroup" yaml:"mergeGroup" mapstructure:"mergeGroup"`
	ViTopic                string `json:"viTopic" yaml:"viTopic" mapstructure:"viTopic"`
	DccTopic               string `json:"dccTopic" yaml:"dccTopic" mapstructure:"dccTopic"`
	Endpoint               string `json:"endpoint" yaml:"endpoint" mapstructure:"endpoint"`
	MaxAttempts            int    `json:"maxAttempts" yaml:"maxAttempts" mapstructure:"maxAttempts"`
	ConsumptionThreadCount int32  `json:"consumptionThreadCount" yaml:"consumptionThreadCount" mapstructure:"consumptionThreadCount"`
	ModeBiz                string `json:"modeBiz" yaml:"modeBiz" mapstructure:"modeBiz"`
}

type Outbound struct {
	FileCluster string `json:"fileCluster" yaml:"fileCluster" mapstructure:"fileCluster"`
	FileAppName string `json:"fileAppName" yaml:"fileAppName" mapstructure:"fileAppName"`
	FileUri     string `json:"fileUri" yaml:"fileUri" mapstructure:"fileUri"`

	MediaCluster string `json:"mediaCluster" yaml:"mediaCluster" mapstructure:"mediaCluster"`
	MediaAppName string `json:"mediaAppName" yaml:"mediaAppName" mapstructure:"mediaAppName"`
	MediaUri     string `json:"mediaUri" yaml:"mediaUri" mapstructure:"mediaUri"`

	Timeout   int  `json:"timeout" yaml:"timeout" mapstructure:"timeout"`
	SaveLocal bool `json:"uploadFailSaveLocal" yaml:"uploadFailSaveLocal" mapstructure:"uploadFailSaveLocal"`
}

type Ffmpeg struct {
	StaticPath string `json:"staticPath" yaml:"staticPath" mapstructure:"staticPath"`
}

type AppConfig struct {
	AppInfo   `yaml:"appInfo" mapstructure:"appInfo"`
	LogInfo   `yaml:"logInfo" mapstructure:"logInfo"`
	RocketMsg `yaml:"rocketMq" mapstructure:"rocketMq"`
	Outbound  `yaml:"outbound" mapstructure:"outbound"`
	Ffmpeg    `yaml:"ffmpeg" mapstructure:"ffmpeg"`

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
