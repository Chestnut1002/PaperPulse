"""抓取并切分 arXiv 论文的全文。

<p>用 arXiv 的 **HTML 版**(而不是 PDF):实测可达、**带结构**(33 个 section),
不必写 PDF 解析。老论文也有 —— arXiv 回填过。

<p>只有 arXiv 来源的论文能走这条路,期刊论文没有可抓的开放全文。这是明确的边界,不是遗漏。
"""

import json
import re
from dataclasses import dataclass
from pathlib import Path

import httpx
from bs4 import BeautifulSoup

from .. import config

ARXIV_HTML = "https://arxiv.org/html/{arxiv_id}"

# 这些节对"这篇论文讲了什么"帮助很小,却可能占掉三分之一篇幅
_SKIPPED_TITLES = re.compile(
    r"^\s*(\d+\.?\s*)?(references|bibliography|acknowledg|appendix|supplement)",
    re.IGNORECASE,
)


class FullTextUnavailable(RuntimeError):
    """这篇论文没有可抓的全文。调用方据此告诉用户"这篇读不了",而不是报 500。"""


@dataclass(frozen=True)
class Section:
    """一节。`index` 从 1 开始 —— 送进上下文时用 `[[§n]]` 标记,模型也用它回指。"""

    index: int
    title: str
    text: str


@dataclass(frozen=True)
class FullText:
    arxiv_id: str
    title: str
    sections: tuple[Section, ...]

    def to_prompt(self) -> str:
        """拼成送进模型的文本。每一节前面带编号标记,模型靠它回指。"""
        parts = [f"论文标题:{self.title}", ""]
        for section in self.sections:
            parts.append(f"[[§{section.index}]] {section.title}")
            parts.append(section.text)
            parts.append("")
        return "\n".join(parts)

    def find(self, index: int) -> Section | None:
        for section in self.sections:
            if section.index == index:
                return section
        return None

    def as_dict(self) -> dict:
        return {
            "arxiv_id": self.arxiv_id,
            "title": self.title,
            "sections": [
                {"index": s.index, "title": s.title, "text": s.text} for s in self.sections
            ],
        }

    @staticmethod
    def from_dict(data: dict) -> "FullText":
        return FullText(
            arxiv_id=data["arxiv_id"],
            title=data["title"],
            sections=tuple(Section(**item) for item in data["sections"]),
        )


def _has_class(tag, *names: str) -> bool:
    classes = tag.get("class") or []
    return any(name in classes for name in names)


def _is_heading(tag) -> bool:
    return tag.name in ("h2", "h3") and _has_class(
        tag, "ltx_title_section", "ltx_title_subsection", "ltx_title_bibliography"
    )


def _is_paragraph(tag) -> bool:
    return _has_class(tag, "ltx_p")


def _inline_latex(soup: BeautifulSoup) -> None:
    """把每个公式元素换成它的 **LaTeX 源码**。

    <p><b>不这么做的话公式是乱码 —— 而且是因为我们抽了两遍。</b>
    arXiv 的 HTML 里每个 `<math>` 都带一个 `application/x-tex` 注解,通用文本抽取会把
    「渲染出来的符号」和「LaTeX 源码」**两份都拿到**,粘在一起:

    <pre>v ∈ V v\\in V        ← 现在抽出来的是这个</pre>

    只取注解里的源码,就得到干净可读的:

    <pre>$v\\in V$</pre>

    <p>实测四篇论文(含 2017 年的)共 1569 个公式,**一个不缺**都带源码。
    所以这不是碰运气 —— 是 arXiv HTML 的固定特性。极少数没有注解的保留原样,交给文本抽取。
    """
    for math in soup.find_all("math"):
        annotation = math.find("annotation", attrs={"encoding": "application/x-tex"})
        if annotation is None:
            continue
        # 包一层 $…$:让模型看得出这是数学而不是普通文字
        math.replace_with(f" ${annotation.get_text().strip()}$ ")


def parse(arxiv_id: str, html: str) -> FullText:
    """把 arXiv 的 HTML 拆成有序的节。

    <p>**按文档顺序遍历标题与段落**,而不是逐层递归 DOM:arXiv 的节是嵌套的
    (`section` 里还有 `section`),顺着兄弟节点走会漏掉子节的内容。
    """
    soup = BeautifulSoup(html, "html.parser")
    for tag in soup(["script", "style", "nav", "footer"]):
        tag.decompose()
    # 公式必须先处理:要赶在文本抽取之前换成源码
    _inline_latex(soup)

    title_tag = soup.find("h1", class_="ltx_title_document")
    title = title_tag.get_text(" ", strip=True) if title_tag else "(无标题)"

    sections: list[Section] = []
    current_title = "正文"
    current_parts: list[str] = []
    skipped = False

    def flush() -> None:
        nonlocal current_parts
        text = re.sub(r"\s+", " ", " ".join(current_parts)).strip()
        # **一个段落都没有的节是空壳** —— 在 LaTeXML 的输出里很常见:父节只负责分组,
        # 内容全在子节里。留着它只会占上下文。
        #
        # 判据是"有没有段落"而不是"正文够不够长":后者会**误伤合法的短节**
        # (比如只有一句话的结论)。
        if current_parts and text and not skipped:
            sections.append(Section(index=len(sections) + 1, title=current_title, text=text))
        current_parts = []

    for node in soup.find_all(lambda tag: _is_heading(tag) or _is_paragraph(tag)):
        if _is_heading(node):
            flush()
            current_title = re.sub(r"\s+", " ", node.get_text(" ", strip=True))
            skipped = bool(_SKIPPED_TITLES.match(current_title))
        elif not skipped:
            current_parts.append(node.get_text(" ", strip=True))
    flush()

    if not sections:
        raise FullTextUnavailable(f"arXiv {arxiv_id} 的 HTML 里没有解析出正文")
    return FullText(arxiv_id=arxiv_id, title=title, sections=tuple(sections))


class FullTextStore:
    """抓全文并落本机缓存。

    <p>**缓存放文件而不是内存**:重启不丢,而且用户重复读同一篇论文时不必反复联网。
    """

    def __init__(self, cache_dir: Path):
        self.cache_dir = cache_dir
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def get(self, arxiv_id: str) -> FullText:
        path = self.cache_dir / f"{_safe_name(arxiv_id)}.json"
        if path.exists():
            return FullText.from_dict(json.loads(path.read_text(encoding="utf-8")))

        full_text = self._fetch(arxiv_id)
        path.write_text(json.dumps(full_text.as_dict(), ensure_ascii=False), encoding="utf-8")
        return full_text

    def _fetch(self, arxiv_id: str) -> FullText:
        url = ARXIV_HTML.format(arxiv_id=arxiv_id)
        try:
            response = httpx.get(url, timeout=config.REQUEST_TIMEOUT,
                                 headers={"User-Agent": config.USER_AGENT}, follow_redirects=True)
        except httpx.HTTPError as exc:
            raise FullTextUnavailable(f"抓取 {url} 失败:{exc}") from exc

        if response.status_code != 200:
            # arXiv 对没有 HTML 版的论文会给 404 —— 那是"这篇不支持",不是"服务器坏了"
            raise FullTextUnavailable(
                f"arXiv {arxiv_id} 没有可用的 HTML 全文(HTTP {response.status_code})"
            )
        return parse(arxiv_id, response.text)


def _safe_name(arxiv_id: str) -> str:
    """arXiv ID 里可能有 `/`(老式编号),不能直接当文件名。"""
    return re.sub(r"[^A-Za-z0-9._-]", "_", arxiv_id)
