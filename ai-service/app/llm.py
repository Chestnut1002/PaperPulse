"""DeepSeek 客户端。

直接用 httpx 走 OpenAI 兼容协议,不引官方 SDK —— 我们只用到 chat/completions 一个接口,
一个 HTTP 调用不值得再背一层依赖。
"""

import json
import time

import httpx

from . import config

# 上游抖动的重试。实测大模型会偶发失败 —— 一次就放弃的话,用户看到的是"服务出错",
# 而实际上再问一遍就好了。
_RETRIES = 2
_RETRY_WAIT_SECONDS = 2.0
_TIMEOUT_SECONDS = 90

# 单次回答的输出上限。**必须显式设**:不设的话是服务端的默认值,而长回答会被它从中间截断。
_MAX_OUTPUT_TOKENS = 4096


class LlmError(RuntimeError):
    """调用大模型失败。调用方据此返回 502,而不是把异常漏成 500。"""


def _post_with_retry(system_prompt: str, user_prompt: str, json_mode: bool) -> httpx.Response:
    """发一次请求;**上游抖动就重试一次**。

    <p>实测大模型会偶发失败(同一次提问,前一次 502、后一次正常)。
    一次就放弃的话,用户看到的是"精读服务出错",而实际上再问一遍就好了。

    <p>**只重试暂时的失败**(超时、连接不上、429、5xx)。4xx 是请求本身有问题,
    重试一百次也一样 —— 那类错误直接抛。
    """
    last_error = ""
    for attempt in range(_RETRIES):
        try:
            response = httpx.post(
                f"{config.DEEPSEEK_BASE_URL}/chat/completions",
                headers={"Authorization": f"Bearer {config.DEEPSEEK_API_KEY}"},
                json={
                    "model": config.DEEPSEEK_MODEL,
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": user_prompt},
                    ],
                    **({"response_format": {"type": "json_object"}} if json_mode else {}),
                    "max_tokens": _MAX_OUTPUT_TOKENS,
                    # 拆解查询是抽取任务,不是创作任务 —— 温度调到 0,同样的输入给同样的结果
                    "temperature": 0.0,
                },
                timeout=_TIMEOUT_SECONDS,
            )
        except httpx.HTTPError as exc:
            last_error = f"连接大模型失败:{exc}"
        else:
            if response.status_code == 200:
                return response
            last_error = f"大模型返回 HTTP {response.status_code}:{response.text[:200]}"
            if response.status_code < 500 and response.status_code != 429:
                raise LlmError(last_error)

        if attempt < _RETRIES - 1:
            time.sleep(_RETRY_WAIT_SECONDS)

    raise LlmError(last_error)


def chat_json(system_prompt: str, user_prompt: str) -> dict:
    """让模型输出一段 JSON 并解析成字典。

    <p>用 `response_format={"type": "json_object"}` 约束输出格式,而不是"在提示词里求它输出 JSON
    然后祈祷" —— 前者是接口层面的保证,后者是运气。注意 DeepSeek 要求提示词里必须出现
    "json" 字样,否则会直接报错,所以调用方的提示词里要写上。
    """
    if not config.DEEPSEEK_API_KEY:
        raise LlmError("未配置 DEEPSEEK_API_KEY,请检查 ai-service/.env")

    response = _post_with_retry(system_prompt, user_prompt, json_mode=True)

    content = _content_of(response)
    try:
        return json.loads(content)
    except json.JSONDecodeError as exc:
        raise LlmError(f"大模型没有返回合法 JSON:{content[:200]}") from exc


def chat_text(system_prompt: str, user_prompt: str) -> str:
    """让模型自由回答,返回纯文本。

    <p><b>问答刻意不用 {@link chat_json}</b>:把长回答塞进 JSON 字符串里,一旦达到输出上限,
    字符串就会被**从中间截断**成一个非法 JSON —— 整次调用报废。
    纯文本答到一半只是短一点,**失败得温和**。

    <p>这不是假想的风险:实测问《Attention Is All You Need》"核心贡献是什么",
    答案长到被截断,JSON 解析直接失败。

    <p>需要结构化输出的地方(查询拆解)仍然用 chat_json。
    """
    return _content_of(_post_with_retry(system_prompt, user_prompt, json_mode=False))


def _content_of(response: httpx.Response) -> str:
    try:
        content = response.json()["choices"][0]["message"]["content"]
    except (KeyError, IndexError, ValueError) as exc:
        raise LlmError(f"大模型响应格式异常:{response.text[:200]}") from exc

    text = (content or "").strip()
    if not text:
        raise LlmError("大模型返回了空回答")
    return text
