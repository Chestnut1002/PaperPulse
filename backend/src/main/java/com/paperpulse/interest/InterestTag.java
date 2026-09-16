package com.paperpulse.interest;

import com.paperpulse.common.ApiException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 兴趣标签词表(F5)。
 *
 * <p><b>为什么是受控词表而不是自由输入:</b>标签最终要喂给检索(REQ-002)与推荐(REQ-004)。
 * 若允许自由输入,「机器学习 / ML / machine learning」会变成三个互不相干的串,
 * 用户兴趣向量没法对齐,也没法拿去查论文。
 *
 * <p><b>为什么标签不直接等于 Semantic Scholar 的 fieldsOfStudy:</b>S2 的 {@code fieldsOfStudy}
 * 只有 23 个大类,「Computer Science」是**一个**值。全站都是 CS 论文的项目里,
 * 用户如果只能选这一项,推荐模块什么都学不到。所以这里的标签是细粒度研究领域,
 * 每个标签另外携带它对应的 S2 过滤值({@link #s2Field})与检索词({@link #s2Query})。
 *
 * <p>词表定义在代码里而非数据库表:受控词表应当随代码版本走、可评审、可 diff。
 * 用户在 {@code user_interest} 表里存的是本枚举的 {@link #key()},不存展示名 ——
 * 这样以后改展示名不影响已有数据。取不到对应枚举的历史 key 会在读取时被过滤掉。
 */
public enum InterestTag {

    // ── 人工智能 ──────────────────────────────────────────
    MACHINE_LEARNING("机器学习", "人工智能", "Computer Science", "machine learning"),
    DEEP_LEARNING("深度学习", "人工智能", "Computer Science", "deep learning"),
    LARGE_LANGUAGE_MODEL("大语言模型", "人工智能", "Computer Science", "large language model"),
    NLP("自然语言处理", "人工智能", "Computer Science", "natural language processing"),
    COMPUTER_VISION("计算机视觉", "人工智能", "Computer Science", "computer vision"),
    REINFORCEMENT_LEARNING("强化学习", "人工智能", "Computer Science", "reinforcement learning"),
    GENERATIVE_MODEL("生成模型", "人工智能", "Computer Science", "generative model"),
    KNOWLEDGE_GRAPH("知识图谱", "人工智能", "Computer Science", "knowledge graph"),
    MULTIMODAL("多模态学习", "人工智能", "Computer Science", "multimodal learning"),

    // ── 信息检索与推荐 ────────────────────────────────────
    RECOMMENDER_SYSTEM("推荐系统", "信息检索与推荐", "Computer Science", "recommender system"),
    INFORMATION_RETRIEVAL("信息检索", "信息检索与推荐", "Computer Science", "information retrieval"),
    RAG("检索增强生成", "信息检索与推荐", "Computer Science", "retrieval-augmented generation"),
    GRAPH_NEURAL_NETWORK("图神经网络", "信息检索与推荐", "Computer Science", "graph neural network"),
    SEQUENTIAL_RECOMMENDATION("序列推荐", "信息检索与推荐", "Computer Science", "sequential recommendation"),
    COLD_START("冷启动推荐", "信息检索与推荐", "Computer Science", "cold-start recommendation"),
    TEXT_EMBEDDING("文本表示与向量检索", "信息检索与推荐", "Computer Science", "text embedding"),

    // ── 可信与可解释 ──────────────────────────────────────
    EXPLAINABLE_AI("可解释人工智能", "可信与可解释", "Computer Science", "explainable artificial intelligence"),
    CAUSAL_INFERENCE("因果推断", "可信与可解释", "Computer Science", "causal inference"),
    FAIRNESS("公平性与偏见", "可信与可解释", "Computer Science", "algorithmic fairness"),
    UNCERTAINTY("不确定性建模", "可信与可解释", "Computer Science", "uncertainty quantification"),
    AI_SAFETY("人工智能安全", "可信与可解释", "Computer Science", "AI safety"),

    // ── 数据与系统 ────────────────────────────────────────
    DATABASE("数据库系统", "数据与系统", "Computer Science", "database systems"),
    DISTRIBUTED_SYSTEM("分布式系统", "数据与系统", "Computer Science", "distributed systems"),
    SOFTWARE_ENGINEERING("软件工程", "数据与系统", "Computer Science", "software engineering"),
    SECURITY("信息安全", "数据与系统", "Computer Science", "computer security"),
    HCI("人机交互", "数据与系统", "Computer Science", "human-computer interaction"),
    PROGRAMMING_LANGUAGE("程序语言", "数据与系统", "Computer Science", "programming languages"),

    // ── 数学基础 ──────────────────────────────────────────
    OPTIMIZATION("最优化", "数学基础", "Mathematics", "optimization"),
    PROBABILITY_STATISTICS("概率与统计", "数学基础", "Mathematics", "probability and statistics"),

    // ── 交叉学科 ──────────────────────────────────────────
    BIOINFORMATICS("生物信息学", "交叉学科", "Biology", "bioinformatics"),
    MEDICAL_AI("医学人工智能", "交叉学科", "Medicine", "medical artificial intelligence"),
    ROBOTICS("机器人学", "交叉学科", "Engineering", "robotics"),
    COMPUTATIONAL_SOCIAL_SCIENCE("计算社会科学", "交叉学科", "Sociology", "computational social science"),
    EDUCATION_TECHNOLOGY("教育技术", "交叉学科", "Education", "educational technology");

    private final String displayName;
    private final String category;
    private final String s2Field;
    private final String s2Query;

    InterestTag(String displayName, String category, String s2Field, String s2Query) {
        this.displayName = displayName;
        this.category = category;
        this.s2Field = s2Field;
        this.s2Query = s2Query;
    }

    /**
     * 稳定标识,存进数据库、出现在 API 里的就是它。改名不影响已有数据。
     *
     * <p><b>必须带 {@link Locale#ROOT}:</b>不带 Locale 的 {@code toLowerCase()} 在土耳其语环境下
     * 会把 {@code I} 变成无点的 {@code ı},于是 {@code INFORMATION_RETRIEVAL} 得到
     * {@code ınformation_retrieval}。它和库里存的 {@code information_retrieval} 对不上,
     * 读取时会被 {@link #findByKey} 判为"词表里没有这个标签"而**静默过滤掉** ——
     * 用户看不到任何报错,只是已保存的兴趣凭空少了几项。
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String displayName() {
        return displayName;
    }

    public String category() {
        return category;
    }

    /** 对应的 Semantic Scholar {@code fieldsOfStudy} 过滤值。 */
    public String s2Field() {
        return s2Field;
    }

    /** 用于检索的细化关键词,配合 {@link #s2Field()} 缩小范围。 */
    public String s2Query() {
        return s2Query;
    }

    /**
     * 按 key 查标签,查不到抛 400。
     *
     * <p>把"未知标签"当成客户端的参数错误而不是静默忽略:静默忽略会让用户以为保存成功了,
     * 实际却少存了几个。
     */
    public static InterestTag fromKey(String key) {
        return findByKey(key).orElseThrow(() -> ApiException.badRequest("未知的兴趣标签:" + key));
    }

    public static Optional<InterestTag> findByKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(tag -> tag.key().equals(key))
                .findFirst();
    }

    /**
     * 全量词表,按分组聚合。
     *
     * <p>用 {@link LinkedHashMap} 保持枚举的声明顺序 —— 前端直接按返回顺序渲染即可,
     * 不必自己再排一遍。
     */
    public static Map<String, List<InterestTag>> groupedByCategory() {
        return Arrays.stream(values())
                .collect(Collectors.groupingBy(InterestTag::category, LinkedHashMap::new, Collectors.toList()));
    }
}
