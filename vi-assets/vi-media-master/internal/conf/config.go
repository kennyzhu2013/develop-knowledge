package conf

import (
	"errors"
	"github.com/spf13/viper"
)

type Ims struct {
	Host  string `json:"host" yaml:"host" mapstructure:"host"`
	Start int    `json:"start" yaml:"start" mapstructure:"start"`
	End   int    `json:"end" yaml:"end" mapstructure:"end"`
}

type Registry struct {
	ClusterName string   `json:"clusterName" yaml:"clusterName" mapstructure:"clusterName"`
	AppName     string   `json:"appName" yaml:"appName" mapstructure:"appName"`
	AppId       string   `json:"appId" yaml:"appId" mapstructure:"appId"`
	Address     []string `json:"address" yaml:"address" mapstructure:"address"`
	Weight      int      `json:"weight" yaml:"weight" mapstructure:"weight"`
}

type Messaging struct {
	Namespace   string `json:"namespace" yaml:"namespace" mapstructure:"namespace"`
	AccessKey   string `json:"accessKey" yaml:"accessKey" mapstructure:"accessKey"`
	SecretKey   string `json:"secretKey" yaml:"secretKey" mapstructure:"secretKey"`
	Topic       string `json:"topic" yaml:"topic" mapstructure:"topic"`
	Endpoint    string `json:"endpoint" yaml:"endpoint" mapstructure:"endpoint"`
	MaxAttempts int    `json:"maxAttempts" yaml:"maxAttempts" mapstructure:"maxAttempts"`
}

type MediaSource struct {
	Inbound struct {
		AppName          string `json:"appName" yaml:"appName" mapstructure:"appName"`
		Uri              string `json:"uri" yaml:"uri" mapstructure:"uri"`
		WriteTimeout     int    `json:"writeTimeout" yaml:"writeTimeout" mapstructure:"writeTimeout"`
		HandshakeTimeout int    `json:"handshakeTimeout" yaml:"handshakeTimeout" mapstructure:"handshakeTimeout"`
	} `json:"inbound" yaml:"inbound" mapstructure:"inbound"`
	Outbound struct {
		ClusterName      string `json:"clusterName" yaml:"clusterName" mapstructure:"clusterName"`
		AppName          string `json:"appName" yaml:"appName" mapstructure:"appName"`
		Uri              string `json:"uri" yaml:"uri" mapstructure:"uri"`
		TTS              string `json:"tts" yaml:"tts" mapstructure:"tts"`
		WriteTimeout     int    `json:"writeTimeout" yaml:"writeTimeout" mapstructure:"writeTimeout"`
		HandshakeTimeout int    `json:"handshakeTimeout" yaml:"handshakeTimeout" mapstructure:"handshakeTimeout"`
	} `json:"outbound" yaml:"outbound" mapstructure:"outbound"`
}

type Cache struct {
	Cdn struct {
		ClusterName       string `json:"clusterName" yaml:"clusterName" mapstructure:"clusterName"`
		AppName           string `json:"appName" yaml:"appName" mapstructure:"appName"`
		Uri               string `json:"uri" yaml:"uri" mapstructure:"uri"`
		AudioData         string `json:"audioData" yaml:"audioData" mapstructure:"audioData"`
		VideoData         string `json:"videoData" yaml:"videoData" mapstructure:"videoData"`
		AudioLoadBalancer string `json:"audioLoadBalancer" yaml:"audioLoadBalancer" mapstructure:"audioLoadBalancer"`
		VideoLoadBalancer string `json:"videoLoadBalancer" yaml:"videoLoadBalancer" mapstructure:"videoLoadBalancer"`
		AudioTimeout      int    `json:"audioTimeout" yaml:"audioTimeout" mapstructure:"audioTimeout"`
		VideoTimeout      int    `json:"videoTimeout" yaml:"videoTimeout" mapstructure:"videoTimeout"`
		Startup           bool   `json:"startup" yaml:"startup" mapstructure:"startup"`
	} `json:"cdn" yaml:"cdn" mapstructure:"cdn"`
	CircuitBreaker struct {
		MaxRequests         uint32 `json:"maxRequests" yaml:"maxRequests" mapstructure:"maxRequests"`
		Interval            int    `json:"interval" yaml:"interval" mapstructure:"interval"`
		Timeout             int    `json:"timeout" yaml:"timeout" mapstructure:"timeout"`
		ConsecutiveFailures uint32 `json:"consecutiveFailures" yaml:"consecutiveFailures" mapstructure:"consecutiveFailures"`
	} `json:"circuitBreaker" yaml:"circuitBreaker" mapstructure:"circuitBreaker"`
	BaseAudioPath    string `json:"baseAudioPath" yaml:"baseAudioPath" mapstructure:"baseAudioPath"`
	BaseVideoPath    string `json:"baseVideoPath" yaml:"baseVideoPath" mapstructure:"baseVideoPath"`
	MaxAudioCapacity int    `json:"maxAudioCapacity" yaml:"maxAudioCapacity" mapstructure:"maxAudioCapacity"`
	MaxVideoCapacity int    `json:"maxVideoCapacity" yaml:"maxVideoCapacity" mapstructure:"maxVideoCapacity"`
}

type Volume struct {
	Ring string `json:"ring" yaml:"ring" mapstructure:"ring"`
	Call string `json:"call" yaml:"call" mapstructure:"call"`
}

type DB struct {
	Mem     Volume `json:"mem" yaml:"mem" mapstructure:"mem"`
	Disk    Volume `json:"disk" yaml:"disk" mapstructure:"disk"`
	Rewrite string `json:"rewrite" yaml:"rewrite" mapstructure:"rewrite"`
}

type LifeSpan struct {
	Ring int `json:"ring" yaml:"ring" mapstructure:"ring"`
	Call int `json:"call" yaml:"call" mapstructure:"call"`
}

type Data struct {
	Mem struct {
		Ring     string   `json:"ring" yaml:"ring" mapstructure:"ring"`
		Call     string   `json:"call" yaml:"call" mapstructure:"call"`
		LifeSpan LifeSpan `json:"lifeSpan" yaml:"lifeSpan" mapstructure:"lifeSpan"`
	} `json:"mem" yaml:"mem" mapstructure:"mem"`
	Disk struct {
		Ring     string   `json:"ring" yaml:"ring" mapstructure:"ring"`
		Call     string   `json:"call" yaml:"call" mapstructure:"call"`
		LifeSpan LifeSpan `json:"lifeSpan" yaml:"lifeSpan" mapstructure:"lifeSpan"`
	} `json:"disk" yaml:"disk" mapstructure:"disk"`
	Cleanup string `json:"cleanup" yaml:"cleanup" mapstructure:"cleanup"`
}

type Storage struct {
	DB   DB   `json:"db" yaml:"db" mapstructure:"db"`
	Data Data `json:"data" yaml:"data" mapstructure:"data"`
}

type Monitor struct {
	CPU struct {
		Enable    bool    `json:"enable" yaml:"enable" mapstructure:"enable"`
		Tick      int     `json:"tick" yaml:"tick" mapstructure:"tick"`
		Interval  int     `json:"interval" yaml:"interval" mapstructure:"interval"`
		Threshold float64 `json:"threshold" yaml:"threshold" mapstructure:"threshold"`
	} `json:"cpu" yaml:"cpu" mapstructure:"cpu"`
}

type Logger struct {
	File  string `json:"file" yaml:"file" mapstructure:"file"`
	Path  string `json:"path" yaml:"path" mapstructure:"path"`
	Level string `json:"level" yaml:"level" mapstructure:"level"`
}

type AppConfig struct {
	ApiAddress string `json:"apiAddress" yaml:"apiAddress" mapstructure:"apiAddress"`
	ServerKey  string `json:"serverKey" yaml:"serverKey" mapstructure:"serverKey"`
	ServerCert string `json:"serverCert" yaml:"serverCert" mapstructure:"serverCert"`

	Ims         Ims         `json:"ims" yaml:"ims" mapstructure:"ims"`
	Registry    Registry    `json:"registry" yaml:"registry" mapstructure:"registry"`
	Messaging   Messaging   `json:"message" yaml:"message" mapstructure:"message"`
	MediaSource MediaSource `json:"mediaSource" yaml:"mediaSource" mapstructure:"mediaSource"`
	Cache       Cache       `json:"cache" yaml:"cache" mapstructure:"cache"`
	Storage     Storage     `json:"storage" yaml:"storage" mapstructure:"storage"`
	Monitor     Monitor     `json:"monitor" yaml:"monitor" mapstructure:"monitor"`
	Logger      Logger      `json:"logger" yaml:"logger" mapstructure:"logger"`
}

var App AppConfig

func Load(filePath string) error {
	if len(filePath) == 0 {
		return errors.New("empty config file path")
	}
	v := viper.New()
	v.SetConfigFile(filePath)
	if err := v.ReadInConfig(); err != nil {
		return err
	}
	if err := v.Unmarshal(&App); err != nil {
		return err
	}
	return nil
}