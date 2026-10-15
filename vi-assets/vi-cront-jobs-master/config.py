import os
import logging.config
import yaml
from jinja2 import Template

# 读取环境变量
env_dist = os.environ

env = env_dist.get("CRONT_ENV", "production")

if env == "production":
    config_dir = "config"
elif env == "south":
    config_dir = "config.south"
elif env == "north":
    config_dir = "config.north"
elif env == "test":
    config_dir = "config.test"
else:
    config_dir = "config.local"

pwd = os.path.dirname(__file__)
config_path = os.path.join(pwd, config_dir, "config.yaml")

with open(config_path, "r", encoding="utf-8") as f:
    raw_content = f.read()
    # 用Jinja2渲染，自动替换{{变量}}为环境变量中的值
    template = Template(raw_content)
    rendered_content = template.render(os.environ)
    settings = yaml.safe_load(rendered_content)


logging_config_path = os.path.join(pwd, config_dir, "logging.yaml")
with open(logging_config_path, "r", encoding="utf-8") as f:
    logging_settings = yaml.safe_load(f.read())

if not os.path.exists(settings["LOGGING_PATH"]):
    os.mkdir(settings["LOGGING_PATH"])
logging.config.dictConfig(logging_settings)