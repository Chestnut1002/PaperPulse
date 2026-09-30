"""离线评测(REQ-006)。

这是**离线批处理**,不是服务接口 —— 它跑一次得出数字,不对外提供 API。
放在 ai-service 下是因为阶段 2/3 要用的 embedding 与 RecBole 都在 Python 侧。
"""
