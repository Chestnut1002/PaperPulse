"""检索 Agent 的查询拆解:纯逻辑,不联网。

这里盯的是"模型不听话"的各种形状 —— 提示词写了要求,模型仍可能给别的形状,
规整逻辑必须兜得住,否则坏数据会一路走到检索请求里。
"""

from app.agent import to_plan

CURRENT_YEAR = 2026


def plan(data: dict, fallback: str = "原始查询"):
    return to_plan(data, CURRENT_YEAR, fallback_keywords=fallback)


class TestKeywords:
    def test_正常字符串原样返回(self):
        assert plan({"keywords": "contrastive learning recommendation"}).keywords == (
            "contrastive learning recommendation"
        )

    def test_首尾空白被去掉(self):
        assert plan({"keywords": "  diffusion models  "}).keywords == "diffusion models"

    def test_模型返回数组时拼成查询串(self):
        # 实测模型经常无视"返回字符串"的要求给出数组。
        # 若直接 str(),会得到 "['a', 'b']" 这种垃圾去检索,所以必须处理。
        result = plan({"keywords": ["contrastive learning", "recommendation"]})
        assert result.keywords == "contrastive learning recommendation"

    def test_数组里的空白项被跳过(self):
        result = plan({"keywords": ["diffusion models", "  ", ""]})
        assert result.keywords == "diffusion models"

    def test_缺失或空白时回退到原始查询(self):
        assert plan({}, fallback="扩散模型").keywords == "扩散模型"
        assert plan({"keywords": "   "}, fallback="扩散模型").keywords == "扩散模型"
        assert plan({"keywords": []}, fallback="扩散模型").keywords == "扩散模型"


class TestYearRange:
    def test_正常年份原样保留(self):
        result = plan({"keywords": "x", "yearFrom": 2024, "yearTo": 2025})
        assert (result.yearFrom, result.yearTo) == (2024, 2025)

    def test_两位数年份补成两千年(self):
        assert plan({"keywords": "x", "yearFrom": 24}).yearFrom == 2024

    def test_起止反了自动交换而不是报错(self):
        result = plan({"keywords": "x", "yearFrom": 2025, "yearTo": 2020})
        assert (result.yearFrom, result.yearTo) == (2020, 2025)

    def test_超出合理范围视为没有约束(self):
        # 与其把约束当成"到 3025 年",不如当成没限制 —— 后者不会把结果全过滤掉
        assert plan({"keywords": "x", "yearFrom": 3025}).yearFrom is None
        assert plan({"keywords": "x", "yearFrom": 1200}).yearFrom is None

    def test_不是数字就当作没给(self):
        assert plan({"keywords": "x", "yearFrom": "近几年"}).yearFrom is None

    def test_允许到明年(self):
        # 有些论文标注的是下一年,不该把它过滤掉
        assert plan({"keywords": "x", "yearFrom": CURRENT_YEAR + 1}).yearFrom == CURRENT_YEAR + 1

    def test_未给年份时为_None(self):
        result = plan({"keywords": "x"})
        assert result.yearFrom is None and result.yearTo is None


class TestRationale:
    def test_原样保留(self):
        assert plan({"keywords": "x", "rationale": "因为这样搜更准"}).rationale == "因为这样搜更准"

    def test_缺失时为空串而不是_None(self):
        assert plan({"keywords": "x"}).rationale == ""
