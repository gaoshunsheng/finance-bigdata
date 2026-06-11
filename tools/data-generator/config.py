"""
配置加载模块 — 从 config.yaml 读取配置，支持环境变量覆盖。

使用方式:
    from config import get_config
    cfg = get_config()
    print(cfg["mysql"]["host"])
"""

import os
from pathlib import Path
from datetime import timezone, timedelta

import yaml
from loguru import logger

# 北京时间
BEIJING_TZ = timezone(timedelta(hours=8))

# 配置文件路径 (与本文件同目录)
_CONFIG_PATH = Path(__file__).parent / "config.yaml"

_config_cache: dict | None = None


def _deep_merge(base: dict, override: dict) -> dict:
    """深度合并两个字典，override 覆盖 base 的值。"""
    result = base.copy()
    for key, value in override.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = _deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def _apply_env_overrides(cfg: dict) -> dict:
    """
    从环境变量覆盖配置项。

    环境变量命名规则: YAML 路径用下划线连接，全部大写
    例如: mysql.host -> MYSQL_HOST, kafka.topics.credit_query -> KAFKA_TOPICS_CREDIT_QUERY
    """
    env_map = {
        "MYSQL_HOST": ("mysql", "host"),
        "MYSQL_PORT": ("mysql", "port"),
        "MYSQL_USER": ("mysql", "user"),
        "MYSQL_PASSWORD": ("mysql", "password"),
        "MYSQL_DATABASE": ("mysql", "database"),
        "KAFKA_BOOTSTRAP_SERVERS": ("kafka", "bootstrap_servers"),
        "REDIS_HOST": ("redis", "host"),
        "REDIS_PORT": ("redis", "port"),
        "REDIS_PASSWORD": ("redis", "password"),
        "HBASE_ZK_QUORUM": ("hbase", "zk_quorum"),
        "HBASE_ZK_PORT": ("hbase", "zk_port"),
        "DECISION_URL": ("decision", "url"),
        "DECISION_AUTH_SECRET": ("decision", "auth_secret"),
        "ES_HOST": ("elasticsearch", "host"),
        "ES_PORT": ("elasticsearch", "port"),
        "HDFS_NAMENODE_URL": ("hdfs", "namenode_url"),
        "HIVE_HOST": ("hive", "host"),
        "HIVE_PORT": ("hive", "port"),
    }

    for env_key, path in env_map.items():
        env_value = os.getenv(env_key)
        if env_value is not None:
            # 沿路径设置值
            target = cfg
            for key in path[:-1]:
                target = target.setdefault(key, {})
            # 端口类参数转为 int
            leaf_key = path[-1]
            if leaf_key in ("port",):
                env_value = int(env_value)
            target[leaf_key] = env_value
            logger.debug(f"环境变量覆盖: {'.'.join(path)} = {env_value}")

    return cfg


def get_config(config_path: str | Path | None = None, reload: bool = False) -> dict:
    """
    加载配置文件，支持缓存和环境变量覆盖。

    Args:
        config_path: 配置文件路径，默认为 config.yaml
        reload: 是否强制重新加载

    Returns:
        配置字典
    """
    global _config_cache

    if _config_cache is not None and not reload:
        return _config_cache

    path = Path(config_path) if config_path else _CONFIG_PATH
    if not path.exists():
        raise FileNotFoundError(f"配置文件不存在: {path}")

    with open(path, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)

    cfg = _apply_env_overrides(cfg)
    _config_cache = cfg

    logger.info(f"配置已加载: {path}")
    return cfg


def beijing_now() -> str:
    """返回当前北京时间字符串 (yyyy-MM-dd HH:mm:ss)。"""
    from datetime import datetime
    return datetime.now(BEIJING_TZ).strftime("%Y-%m-%d %H:%M:%S")
