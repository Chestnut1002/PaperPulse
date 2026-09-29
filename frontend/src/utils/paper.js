/** 论文在列表里的展示文案。检索结果与"我的论文"共用,避免两处各写一份。 */

/** 作者:最多列三位,更多的用"等"收尾;一个都没有时明说,而不是留一片空白。 */
export function authorLine(paper) {
  const authors = paper?.authors ?? []
  if (!authors.length) {
    return '作者未提供'
  }
  return authors.length > 3 ? authors.slice(0, 3).join(', ') + ' 等' : authors.join(', ')
}

/** 元信息行:作者 · 期刊 · 年份 · 来源。缺的项自动跳过,不留出多余的分隔点。 */
export function paperMeta(paper) {
  return [authorLine(paper), paper?.venue, paper?.publicationYear, paper?.sourceDisplayName]
    .filter(Boolean)
    .join(' · ')
}

/** 后端给的是 ISO 字符串,直接显示会是一串带纳秒的时间戳。 */
export function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '—'
}
