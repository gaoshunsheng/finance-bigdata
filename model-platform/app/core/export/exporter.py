"""
模型导出模块

支持将训练好的信用风险模型导出为 PMML 或 ONNX 格式,
便于跨平台部署和推理服务集成。
"""
import json
import logging
import pickle
import threading
from datetime import datetime
from pathlib import Path
from typing import Any, Optional
from xml.etree.ElementTree import Element, SubElement, ElementTree, indent

import numpy as np
from sklearn.linear_model import LogisticRegression

from app import config

logger = logging.getLogger(__name__)

# ── 可选依赖 ──────────────────────────────────────────────
try:
    from skl2onnx import convert_sklearn
    _HAS_SKL2ONNX = True
except ImportError:
    _HAS_SKL2ONNX = False

try:
    import onnxmltools
    _HAS_ONNXMLTOOLS = True
except ImportError:
    _HAS_ONNXMLTOOLS = False

try:
    import onnxruntime as ort
    _HAS_ONNXRUNTIME = True
except ImportError:
    _HAS_ONNXRUNTIME = False


class ModelExporter:
    """模型导出服务, 负责 PMML / ONNX 格式转换与持久化。"""

    def __init__(self) -> None:
        self._exports: dict[str, dict[str, Any]] = {}
        self._id_counter: int = 0
        self._lock = threading.Lock()
        self._storage_dir: Path = config.MODEL_STORAGE_DIR / "exports"
        self._storage_dir.mkdir(parents=True, exist_ok=True)

    # ── 内部工具 ──────────────────────────────────────────

    def _next_id(self) -> str:
        """生成自增导出 ID (线程安全)。"""
        with self._lock:
            self._id_counter += 1
            return f"export_{self._id_counter:06d}"

    @staticmethod
    def _now_beijing() -> str:
        """返回当前北京时间字符串。"""
        from zoneinfo import ZoneInfo
        return datetime.now(ZoneInfo("Asia/Shanghai")).strftime("%Y-%m-%d %H:%M:%S")

    # ── PMML 导出 ─────────────────────────────────────────

    def export_pmml(
        self,
        model_result: dict[str, Any],
        version: Optional[str] = None,
    ) -> dict[str, Any]:
        """
        将模型导出为 PMML 格式。

        Parameters
        ----------
        model_result : dict
            必须包含:
              - estimator : 训练好的模型对象 (sklearn / xgboost / lightgbm)
              - model_id  : 模型标识
              - algorithm : 算法类型 (LR / XGBOOST / LIGHTGBM)
        version : str, optional
            版本标签, 默认使用时间戳。

        Returns
        -------
        dict
            导出结果, 包含 model_id / format / file_path / file_size_bytes / exported_at。
        """
        estimator = model_result.get("estimator") or model_result.get("_best_estimator")
        model_id = model_result.get("model_id")
        algorithm = model_result.get("algorithm", "")

        if estimator is None or model_id is None:
            raise ValueError("model_result 必须包含 'estimator'/'_best_estimator' 和 'model_id' 字段")

        version_tag = version or datetime.now().strftime("v%Y%m%d%H%M%S")

        if algorithm.upper() == "LR":
            pmml_content = self._build_lr_pmml(estimator, model_id, version_tag)
        elif algorithm.upper() in ("XGBOOST", "LIGHTGBM"):
            pmml_content = self._build_tree_pmml(estimator, model_id, version_tag, algorithm)
        else:
            raise ValueError(f"不支持的算法类型: {algorithm}, 暂仅支持 LR / XGBOOST / LIGHTGBM")

        file_path = self._save_file(
            model_id=model_id,
            version_tag=version_tag,
            suffix=".pmml",
            content=pmml_content,
        )

        export_record = {
            "export_id": self._next_id(),
            "model_id": model_id,
            "format": "PMML",
            "version": version_tag,
            "file_path": str(file_path),
            "file_size_bytes": file_path.stat().st_size,
            "exported_at": self._now_beijing(),
        }
        self._exports[export_record["export_id"]] = export_record
        logger.info("PMML 导出完成: model_id=%s, path=%s", model_id, file_path)
        return export_record

    # -- LR -> PMML XML ---------------------------------------------------

    @staticmethod
    def _build_lr_pmml(
        estimator: LogisticRegression,
        model_id: str,
        version: str,
    ) -> str:
        """
        将逻辑回归模型的系数和截距序列化为 PMML XML。
        使用 sklearn2pmml 的精简模式: 只保留 RegressionModel 段。
        """
        coef = estimator.coef_[0]  # shape: (n_features,)
        intercept = estimator.intercept_[0]

        # 尝试获取特征名
        feature_names = getattr(estimator, "feature_names_in_", None)
        if feature_names is None:
            feature_names = [f"x{i}" for i in range(len(coef))]

        # -- 构建 PMML XML --
        pmml = Element("PMML", version="4.4", xmlns="http://www.dmg.org/PMML-4_4")

        header = SubElement(pmml, "Header")
        SubElement(header, "Application", name="finance-bigdata-model-platform", version=version)
        timestamp = SubElement(header, "Timestamp")
        timestamp.text = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        data_dict = SubElement(pmml, "DataDictionary")
        for fname in feature_names:
            SubElement(data_dict, "DataField", name=fname, optype="continuous", dataType="double")
        SubElement(data_dict, "DataField", name="label", optype="categorical", dataType="integer")

        model_el = SubElement(pmml, "RegressionModel", modelName=f"LR_{model_id}", functionName="classification")
        mining = SubElement(model_el, "MiningSchema")
        for fname in feature_names:
            SubElement(mining, "MiningField", name=fname, usageType="active")
        SubElement(mining, "MiningField", name="label", usageType="target")

        output_el = SubElement(model_el, "Output")
        SubElement(output_el, "OutputField", name="probability(0)", feature="probability", value="0")
        SubElement(output_el, "OutputField", name="probability(1)", feature="probability", value="1")

        reg_table = SubElement(model_el, "RegressionTable", targetCategory="1")
        for fname, c in zip(feature_names, coef):
            SubElement(reg_table, "NumericPredictor", name=fname, coefficient=str(float(c)))
        SubElement(reg_table, "NumericPredictor", name="intercept", coefficient=str(float(intercept)))

        # 负类: 系数取反
        reg_table_neg = SubElement(model_el, "RegressionTable", targetCategory="0")
        for fname, c in zip(feature_names, coef):
            SubElement(reg_table_neg, "NumericPredictor", name=fname, coefficient=str(float(-c)))
        SubElement(reg_table_neg, "NumericPredictor", name="intercept", coefficient=str(float(-intercept)))

        indent(pmml, space="  ")
        from xml.etree.ElementTree import tostring
        return tostring(pmml, encoding="unicode", xml_declaration=True)

    # -- Tree -> PMML (JSON wrapper) --------------------------------------

    @staticmethod
    def _build_tree_pmml(
        estimator: Any,
        model_id: str,
        version: str,
        algorithm: str,
    ) -> str:
        """
        将 XGBoost / LightGBM 模型序列化为嵌入 JSON 的 PMML 文件。
        由于完整 PMML TreeModel 生成需要 jpmml 等外部依赖,
        这里采用实用方案: 将 booster 配置 + 模型 JSON 嵌入 PMML Extension 节点。
        """
        pmml = Element("PMML", version="4.4", xmlns="http://www.dmg.org/PMML-4_4")

        header = SubElement(pmml, "Header")
        SubElement(header, "Application", name="finance-bigdata-model-platform", version=version)
        ts = SubElement(header, "Timestamp")
        ts.text = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

        ext = SubElement(pmml, "Extension", name="tree_model_json", extractor=algorithm)

        if algorithm.upper() == "XGBOOST":
            booster = estimator.get_booster() if hasattr(estimator, "get_booster") else estimator
            model_json = booster.save_model(raw_format="json") if hasattr(booster, "save_model") else ""
            config_str = json.dumps(
                estimator.get_params() if hasattr(estimator, "get_params") else {},
                default=str,
                ensure_ascii=False,
            )
        elif algorithm.upper() == "LIGHTGBM":
            booster = estimator.booster_ if hasattr(estimator, "booster_") else estimator
            if hasattr(booster, "model_to_string"):
                model_json = booster.model_to_string()
            else:
                model_json = ""
            config_str = json.dumps(
                estimator.get_params() if hasattr(estimator, "get_params") else {},
                default=str,
                ensure_ascii=False,
            )
        else:
            model_json = ""
            config_str = "{}"

        # 将 booster config 和模型 JSON 写入 Extension
        config_child = SubElement(ext, "Extension", name="booster_config")
        config_child.text = config_str
        model_child = SubElement(ext, "Extension", name="model_dump")
        model_child.text = model_json

        indent(pmml, space="  ")
        from xml.etree.ElementTree import tostring
        return tostring(pmml, encoding="unicode", xml_declaration=True)

    # ── ONNX 导出 ─────────────────────────────────────────

    def export_onnx(
        self,
        model_result: dict[str, Any],
        feature_names: list[str],
        version: Optional[str] = None,
    ) -> dict[str, Any]:
        """
        将模型导出为 ONNX 格式。

        Parameters
        ----------
        model_result : dict
            同 export_pmml 的 model_result。
        feature_names : list[str]
            特征名列表, 用于定义 ONNX 输入。
        version : str, optional
            版本标签。

        Returns
        -------
        dict
            导出结果。
        """
        estimator = model_result.get("estimator") or model_result.get("_best_estimator")
        model_id = model_result.get("model_id")
        algorithm = model_result.get("algorithm", "")

        if estimator is None or model_id is None:
            raise ValueError("model_result 必须包含 'estimator'/'_best_estimator' 和 'model_id' 字段")
        if not feature_names:
            raise ValueError("导出 ONNX 需要提供 feature_names")

        version_tag = version or datetime.now().strftime("v%Y%m%d%H%M%S")
        n_features = len(feature_names)

        # 定义输入类型
        try:
            from onnxconverter_common.data_types import FloatTensorType
            initial_type = [("features", FloatTensorType([None, n_features]))]
        except ImportError:
            # fallback: 使用 skl2onnx 内置的类型
            from skl2onnx.common.data_types import FloatTensorType
            initial_type = [("features", FloatTensorType([None, n_features]))]

        if algorithm.upper() == "LR":
            onnx_model = self._convert_lr_onnx(estimator, initial_type)
        elif algorithm.upper() == "XGBOOST":
            onnx_model = self._convert_xgboost_onnx(estimator, initial_type)
        elif algorithm.upper() == "LIGHTGBM":
            onnx_model = self._convert_lightgbm_onnx(estimator, initial_type)
        else:
            raise ValueError(f"不支持的算法类型: {algorithm}")

        # 序列化到字节
        onnx_bytes = onnx_model.SerializeToString()
        file_path = self._save_file(
            model_id=model_id,
            version_tag=version_tag,
            suffix=".onnx",
            content=onnx_bytes,
            binary=True,
        )

        export_record = {
            "export_id": self._next_id(),
            "model_id": model_id,
            "format": "ONNX",
            "version": version_tag,
            "file_path": str(file_path),
            "file_size_bytes": file_path.stat().st_size,
            "exported_at": self._now_beijing(),
        }
        self._exports[export_record["export_id"]] = export_record
        logger.info("ONNX 导出完成: model_id=%s, path=%s", model_id, file_path)
        return export_record

    @staticmethod
    def _convert_lr_onnx(estimator: Any, initial_type: list) -> Any:
        """使用 skl2onnx 将逻辑回归模型转换为 ONNX。"""
        if not _HAS_SKL2ONNX:
            raise RuntimeError("skl2onnx 未安装, 无法导出 LR 模型为 ONNX。请执行 pip install skl2onnx")
        return convert_sklearn(estimator, initial_types=initial_type, target_opset=12)

    @staticmethod
    def _convert_xgboost_onnx(estimator: Any, initial_type: list) -> Any:
        """使用 onnxmltools 将 XGBoost 模型转换为 ONNX。"""
        if not _HAS_ONNXMLTOOLS:
            raise RuntimeError("onnxmltools 未安装, 无法导出 XGBoost 模型为 ONNX。请执行 pip install onnxmltools")
        return onnxmltools.convert_xgboost(estimator, initial_types=initial_type, target_opset=12)

    @staticmethod
    def _convert_lightgbm_onnx(estimator: Any, initial_type: list) -> Any:
        """使用 onnxmltools 将 LightGBM 模型转换为 ONNX。"""
        if not _HAS_ONNXMLTOOLS:
            raise RuntimeError("onnxmltools 未安装, 无法导出 LightGBM 模型为 ONNX。请执行 pip install onnxmltools")
        return onnxmltools.convert_lightgbm(estimator, initial_types=initial_type, target_opset=12)

    # ── 文件写入 ──────────────────────────────────────────

    def _save_file(
        self,
        model_id: str,
        version_tag: str,
        suffix: str,
        content: Any,
        binary: bool = False,
    ) -> Path:
        """将内容写入存储目录, 返回文件路径。"""
        filename = f"{model_id}_{version_tag}{suffix}"
        file_path = self._storage_dir / filename
        mode = "wb" if binary else "w"
        encoding = None if binary else "utf-8"
        with open(file_path, mode, encoding=encoding) as f:
            f.write(content)
        return file_path

    # ── 查询 ──────────────────────────────────────────────

    def get_export(self, export_id: str) -> Optional[dict[str, Any]]:
        """
        根据 export_id 获取导出记录。

        Parameters
        ----------
        export_id : str
            导出 ID。

        Returns
        -------
        dict | None
            导出记录, 不存在则返回 None。
        """
        return self._exports.get(export_id)

    def list_exports(
        self,
        model_id: Optional[str] = None,
        fmt: Optional[str] = None,
    ) -> list[dict[str, Any]]:
        """
        列出所有导出记录, 支持按 model_id / format 过滤。

        Parameters
        ----------
        model_id : str, optional
            按模型 ID 过滤。
        fmt : str, optional
            按格式 (PMML / ONNX) 过滤。

        Returns
        -------
        list[dict]
            导出记录列表。
        """
        results = list(self._exports.values())
        if model_id is not None:
            results = [r for r in results if r.get("model_id") == model_id]
        if fmt is not None:
            results = [r for r in results if r.get("format") == fmt.upper()]
        return results


# 全局单例
model_exporter = ModelExporter()
