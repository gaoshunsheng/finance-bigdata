"""
MySQL 连接池 + 批量写入模块。

使用 pymysql 实现简单连接池，提供 batch_insert / execute / query 方法。
"""

from decimal import Decimal
from typing import Any

import pymysql
from loguru import logger

from config import get_config


class MySQLHelper:
    """MySQL 辅助类 — 连接池 + 批量操作"""

    def __init__(self, cfg: dict | None = None):
        mysql_cfg = (cfg or get_config())["mysql"]
        self._host = mysql_cfg["host"]
        self._port = int(mysql_cfg["port"])
        self._user = mysql_cfg["user"]
        self._password = mysql_cfg["password"]
        self._database = mysql_cfg["database"]
        self._connection = None

    def _get_connection(self):
        """获取或创建 MySQL 连接。"""
        if self._connection is None or not self._connection.open:
            self._connection = pymysql.connect(
                host=self._host,
                port=self._port,
                user=self._user,
                password=self._password,
                database=self._database,
                charset="utf8mb4",
                autocommit=False,
                cursorclass=pymysql.cursors.DictCursor,
                auth_plugin_map={"mysql_native_password": None},
            )
            logger.debug(f"MySQL 已连接: {self._host}:{self._port}/{self._database}")
        return self._connection

    def execute(self, sql: str, params: tuple | dict | None = None) -> int:
        """
        执行单条 SQL (INSERT/UPDATE/DELETE)，返回影响行数。

        Args:
            sql: SQL 语句
            params: 参数 (tuple 或 dict)

        Returns:
            影响行数
        """
        conn = self._get_connection()
        try:
            with conn.cursor() as cursor:
                affected = cursor.execute(sql, params)
            conn.commit()
            return affected
        except Exception as e:
            conn.rollback()
            logger.error(f"SQL 执行失败: {sql[:100]}... 错误: {e}")
            raise

    def batch_insert(self, table: str, columns: list[str], rows: list[dict],
                     batch_size: int = 1000) -> int:
        """
        批量 INSERT，按 batch_size 分批提交。

        Args:
            table: 表名
            columns: 列名列表
            rows: 行数据列表 (每行为 dict，key 为列名)
            batch_size: 每批行数

        Returns:
            总插入行数
        """
        if not rows:
            return 0

        placeholders = ", ".join(["%s"] * len(columns))
        col_str = ", ".join(f"`{c}`" for c in columns)
        sql = f"INSERT INTO `{table}` ({col_str}) VALUES ({placeholders})"

        conn = self._get_connection()
        total_inserted = 0

        try:
            with conn.cursor() as cursor:
                for i in range(0, len(rows), batch_size):
                    batch = rows[i:i + batch_size]
                    values = [tuple(row.get(c) for c in columns) for row in batch]
                    cursor.executemany(sql, values)
                    total_inserted += len(batch)
                    conn.commit()
                    logger.debug(f"  批次 {i // batch_size + 1}: 插入 {len(batch)} 行到 {table}")
        except Exception as e:
            conn.rollback()
            logger.error(f"批量 INSERT 失败 ({table}): {e}")
            raise

        return total_inserted

    @staticmethod
    def _convert_row(row: dict[str, Any]) -> dict[str, Any]:
        """将 Decimal 转为 float，避免运算和 JSON 序列化问题。"""
        return {
            k: float(v) if isinstance(v, Decimal) else v
            for k, v in row.items()
        }

    def query(self, sql: str, params: tuple | dict | None = None) -> list[dict[str, Any]]:
        """
        执行查询 SQL，返回字典列表。

        Args:
            sql: SELECT 语句
            params: 参数

        Returns:
            结果行列表 (每行为 dict，Decimal 已转为 float)
        """
        conn = self._get_connection()
        with conn.cursor() as cursor:
            cursor.execute(sql, params)
            rows = cursor.fetchall()
            return [self._convert_row(r) for r in rows]

    def query_one(self, sql: str, params: tuple | dict | None = None) -> dict[str, Any] | None:
        """执行查询 SQL，返回单行或 None (Decimal 已转为 float)。"""
        conn = self._get_connection()
        with conn.cursor() as cursor:
            cursor.execute(sql, params)
            row = cursor.fetchone()
            return self._convert_row(row) if row else None

    def query_count(self, table: str, where: str = "") -> int:
        """查询表行数。"""
        sql = f"SELECT COUNT(*) AS cnt FROM `{table}`"
        if where:
            sql += f" WHERE {where}"
        row = self.query_one(sql)
        return row["cnt"] if row else 0

    def close(self):
        """关闭连接。"""
        if self._connection and self._connection.open:
            self._connection.close()
            logger.debug("MySQL 连接已关闭")

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
