"""
gRPC 推理服务存根

提供 Predict / BatchPredict RPC 方法的 Python Servicer 实现。
实际的 .proto 文件和代码生成应在单独步骤中完成,
此处为 servicer 层的 Python 实现。

预期 proto 定义:

    service ModelInference {
        rpc Predict(PredictRequest) returns (PredictResponse);
        rpc BatchPredict(BatchPredictRequest) returns (BatchPredictResponse);
    }

    message PredictRequest {
        string model_id = 1;
        map<string, double> features = 2;
    }

    message PredictResponse {
        int32 prediction = 1;
        double probability = 2;
        double score = 3;
    }

    message BatchPredictRequest {
        string model_id = 1;
        repeated PredictRequest records = 2;
    }

    message BatchPredictResponse {
        repeated PredictResponse predictions = 1;
        int32 total_count = 2;
        double latency_ms = 3;
    }
"""
import logging
import time
from typing import Any

from app.core.inference.inference_service import InferenceService

logger = logging.getLogger(__name__)

# ── 可选 gRPC 依赖 ────────────────────────────────────────
try:
    from concurrent import futures
    import grpc
    _HAS_GRPC = True
except ImportError:
    _HAS_GRPC = False


class ModelInferenceServicer:
    """
    gRPC 推理服务 Servicer 存根。

    将 gRPC 请求委托给 InferenceService 处理,
    完成协议转换和异常处理。
    """

    def __init__(self, inference_service: InferenceService | None = None) -> None:
        """
        初始化 Servicer。

        Parameters
        ----------
        inference_service : InferenceService, optional
            推理服务实例; 若为 None 则自动创建。
        """
        self._service = inference_service or InferenceService()

    # ── Predict ───────────────────────────────────────────

    def Predict(self, request: Any, context: Any = None) -> dict:
        """
        单条预测 RPC 方法。

        Parameters
        ----------
        request : PredictRequest-like
            需包含:
              - model_id : str
              - features : dict[str, float]  (或 map 类型)
        context : grpc.ServicerContext, optional
            gRPC 上下文 (用于设置错误码等)。

        Returns
        -------
        dict
            prediction / probability / score
        """
        model_id = getattr(request, "model_id", "")
        features = getattr(request, "features", {})

        # 如果 features 是 gRPC MapField, 转为普通 dict
        if hasattr(features, "items"):
            features_dict = dict(features)
        else:
            features_dict = {}

        if not model_id:
            if context and _HAS_GRPC:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("model_id 不能为空")
            return {"prediction": 0, "probability": 0.0, "score": 0.0}

        if not features_dict:
            if context and _HAS_GRPC:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("features 不能为空")
            return {"prediction": 0, "probability": 0.0, "score": 0.0}

        try:
            result = self._service.predict(model_id, features_dict)
            return {
                "prediction": result["prediction"],
                "probability": result["probability"],
                "score": result["score"],
            }
        except ValueError as e:
            logger.warning("Predict 业务异常: %s", e)
            if context and _HAS_GRPC:
                context.set_code(grpc.StatusCode.NOT_FOUND)
                context.set_details(str(e))
            return {"prediction": 0, "probability": 0.0, "score": 0.0}
        except Exception as e:
            logger.exception("Predict 内部错误")
            if context and _HAS_GRPC:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(f"内部错误: {e}")
            return {"prediction": 0, "probability": 0.0, "score": 0.0}

    # ── BatchPredict ──────────────────────────────────────

    def BatchPredict(self, request: Any, context: Any = None) -> dict:
        """
        批量预测 RPC 方法。

        Parameters
        ----------
        request : BatchPredictRequest-like
            需包含:
              - model_id : str
              - records : list[PredictRequest-like]  (每项含 features dict)
        context : grpc.ServicerContext, optional
            gRPC 上下文。

        Returns
        -------
        dict
            predictions / total_count / latency_ms
        """
        model_id = getattr(request, "model_id", "")
        records = getattr(request, "records", [])

        if not model_id:
            if context and _HAS_GRPC:
                context.set_code(grpc.StatusCode.INVALID_ARGUMENT)
                context.set_details("model_id 不能为空")
            return {"predictions": [], "total_count": 0, "latency_ms": 0.0}

        # 将 records 中的每项转换为 features dict
        feature_dicts: list[dict[str, Any]] = []
        for rec in records:
            features = getattr(rec, "features", {})
            if hasattr(features, "items"):
                feature_dicts.append(dict(features))
            else:
                feature_dicts.append({})

        if not feature_dicts:
            return {
                "predictions": [],
                "total_count": 0,
                "latency_ms": 0.0,
            }

        try:
            result = self._service.batch_predict(model_id, feature_dicts)
            return {
                "predictions": result["predictions"],
                "total_count": result["total_count"],
                "latency_ms": result["latency_ms"],
            }
        except ValueError as e:
            logger.warning("BatchPredict 业务异常: %s", e)
            if context and _HAS_GRPC:
                context.set_code(grpc.StatusCode.NOT_FOUND)
                context.set_details(str(e))
            return {"predictions": [], "total_count": 0, "latency_ms": 0.0}
        except Exception as e:
            logger.exception("BatchPredict 内部错误")
            if context and _HAS_GRPC:
                context.set_code(grpc.StatusCode.INTERNAL)
                context.set_details(f"内部错误: {e}")
            return {"predictions": [], "total_count": 0, "latency_ms": 0.0}


# ── gRPC 服务器启动辅助 ────────────────────────────────────

def create_grpc_server(
    port: int = 50051,
    max_workers: int = 10,
) -> Any:
    """
    创建并配置 gRPC 服务器 (需安装 grpcio)。

    Parameters
    ----------
    port : int
        监听端口, 默认 50051。
    max_workers : int
        线程池大小。

    Returns
    -------
    grpc.Server
        已配置但尚未启动的服务器实例。

    Raises
    ------
    RuntimeError
        grpcio 未安装时抛出。
    """
    if not _HAS_GRPC:
        raise RuntimeError(
            "grpcio 未安装, 无法启动 gRPC 服务。请执行 pip install grpcio grpcio-tools"
        )

    from app import config

    actual_port = port or config.GRPC_PORT
    service = InferenceService()
    servicer = ModelInferenceServicer(inference_service=service)

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=max_workers))

    # 注意: 实际 add_ModelInferenceServicer_to_server 需要由 protoc 生成的 _pb2_grpc 模块提供
    # 这里使用通用注册方式作为存根; 正式使用时替换为:
    #   from generated import inference_pb2_grpc
    #   inference_pb2_grpc.add_ModelInferenceServicer_to_server(servicer, server)
    logger.info("gRPC servicer 已创建, 等待 .proto 生成后注册到 server")

    server.add_insecure_port(f"[::]:{actual_port}")
    logger.info("gRPC 服务器已配置, 端口=%d, max_workers=%d", actual_port, max_workers)
    return server
