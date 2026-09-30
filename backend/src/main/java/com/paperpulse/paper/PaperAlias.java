package com.paperpulse.paper;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 论文的别名:某个来源下的身份,其实指向另一行。
 *
 * <p><b>为什么必须有这张表:</b>跨源合并是"认出这一篇其实是那一篇,于是不新建行"。
 * 但如果只做到这一步,合并是**留不住**的 —— 下次检索再返回被合并掉的那个身份时,
 * 按 {@code (来源, 外部 ID)} 查 paper 表查不到,又会新建一行,合并白做。
 * 把"这个身份指向那一行"记下来,之后任何一条身份都能找到同一行。
 *
 * <p>它同时是**后悔药**:万一把两篇不同的论文合错了,删掉这条别名,
 * 下次遇到那个身份就会重新建行,数据自己长回来。
 *
 * <p>与 {@code paper} 一致,**不加外键**(F6 起项目里就没有外键,理由见当年的日志)。
 */
@Entity
@Table(name = "paper_alias",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_paper_alias_source_external_id",
                columnNames = {"source", "external_id"}))
public class PaperAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 指向被采用的那一行。 */
    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaperSource source;

    @Column(name = "external_id", nullable = false, length = 128)
    private String externalId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaperAlias() {
    }

    public PaperAlias(Long paperId, PaperSource source, String externalId, Instant createdAt) {
        this.paperId = paperId;
        this.source = source;
        this.externalId = externalId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPaperId() {
        return paperId;
    }

    public PaperSource getSource() {
        return source;
    }

    public String getExternalId() {
        return externalId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
