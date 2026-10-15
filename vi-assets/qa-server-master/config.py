import logging.config
import os

import yaml

# 读取环境变量
env_dist = os.environ

env = env_dist.get("FLASK_ENV", "local")

if env == "production":
    config_dir = "config.prod"
elif env == "test":
    config_dir = "config.test"
else:
    config_dir = "config.local"


# 读取主配置
pwd = os.path.dirname(__file__)
config_path = os.path.join(pwd, config_dir, "config.yaml")
with open(config_path, "r", encoding="utf-8") as f:
    settings = yaml.safe_load(f.read())

logging_config_path = os.path.join(pwd, config_dir, "logging.yaml")
with open(logging_config_path, "r", encoding="utf-8") as f:
    logging_settings = yaml.safe_load(f.read())

# 初始化日志文件目录
if not os.path.exists(settings["LOGGING_PATH"]):
    os.mkdir(settings["LOGGING_PATH"])
logging.config.dictConfig(logging_settings)
