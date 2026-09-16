package com.paperpulse.interest;

import com.paperpulse.interest.dto.InterestCatalogResponse;
import com.paperpulse.interest.dto.UpdateInterestsRequest;
import com.paperpulse.interest.dto.UserInterestResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 兴趣标签接口(F5)。 */
@RestController
public class InterestController {

    private final InterestService interestService;

    public InterestController(InterestService interestService) {
        this.interestService = interestService;
    }

    /**
     * 标签词表,供前端渲染选择器。
     *
     * <p>与用户无关,所以路径不带 {@code /me}。需要登录才能取:它只服务于登录后的个人资料页,
     * 没必要对匿名者开放。
     */
    @GetMapping("/api/interests")
    public InterestCatalogResponse catalog() {
        return interestService.catalog();
    }

    /** 当前用户的兴趣标签。 */
    @GetMapping("/api/users/me/interests")
    public List<UserInterestResponse> myInterests(@AuthenticationPrincipal Long userId) {
        return interestService.listFor(userId);
    }

    /**
     * 全量替换当前用户的兴趣标签。
     *
     * <p>返回替换后的完整列表,而不是 204 —— 前端提交完通常要立刻重绘,省一次往返。
     * 而且服务端可能对内容做过规整(排序、过滤已下线的标签),返回真实结果比让前端自己猜更可靠。
     */
    @PutMapping("/api/users/me/interests")
    public List<UserInterestResponse> replaceMyInterests(@AuthenticationPrincipal Long userId,
                                                         @Valid @RequestBody UpdateInterestsRequest request) {
        return interestService.replaceFor(userId, request.interests());
    }
}
