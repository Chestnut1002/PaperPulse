package com.paperpulse.paper;

import com.paperpulse.paper.dto.PaperInput;
import com.paperpulse.paper.dto.PaperResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 论文元数据接口(F6)。 */
@RestController
public class PaperController {

    private final PaperService paperService;

    public PaperController(PaperService paperService) {
        this.paperService = paperService;
    }

    /**
     * 把一篇论文的元数据交给后端落库,返回带本地 id 的结果。
     *
     * <p>这是**收藏 / 记录阅读 / 打分的前置步骤**:那三个接口都按本地 id 工作,
     * 而前端从检索结果里拿到的论文还没有本地 id。
     *
     * <p>用 POST 而不是 PUT:URL 里没有资源标识(标识在请求体里),
     * 而且它不是"替换某个已知资源"。语义上是幂等的 upsert,重复提交返回同一行、同一个 id。
     *
     * <p><b>已知限制:</b>元数据由调用方提供,后端不校验它是否真的来自所声称的来源 ——
     * 一个登录用户理论上可以抢占某个 externalId 并写入错误的标题。
     * 当前可接受(只有登录用户能调,且论文只是展示用),但 REQ-002 落地后,
     * 论文应当由后端自己从 Semantic Scholar 拉取写入,那时这条路径会收窄为只读。
     */
    @PostMapping("/api/papers")
    public PaperResponse upsert(@Valid @RequestBody PaperInput input) {
        return PaperResponse.of(paperService.resolve(input));
    }
}
