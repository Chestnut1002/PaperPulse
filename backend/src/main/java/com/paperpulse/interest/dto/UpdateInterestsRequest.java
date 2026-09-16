package com.paperpulse.interest.dto;

import com.paperpulse.interest.InterestService;
import com.paperpulse.interest.UserInterest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 全量替换用户的兴趣标签。
 *
 * <p><b>语义是"替换"而不是"追加"</b>:请求体就是提交后的最终状态。
 * 要删掉某个标签,不传它即可;要清空,传空数组。
 * 用 PUT 而不是 POST 正是为了表达这种幂等语义 —— 同样的请求发两次,结果一样。
 *
 * @param interests 提交后的完整标签列表
 */
public record UpdateInterestsRequest(

        // 刻意区分"没传这个字段"与"传了空数组":前者是客户端漏了(400),
        // 后者是明确的"清空兴趣"意图。用 @NotNull 把两者分开。
        @NotNull(message = "interests 不能为空;若要清空兴趣请传空数组 []")
        @Size(max = InterestService.MAX_TAGS, message = "兴趣标签最多 " + InterestService.MAX_TAGS + " 个")
        @Valid
        List<Item> interests) {

    /**
     * @param tag    标签 key,须在词表内
     * @param weight 兴趣强度 1~5
     */
    public record Item(
            @NotBlank(message = "标签不能为空")
            String tag,

            // 用 Integer 而不是 int:基本类型遇到 null 会先被反序列化成 0,
            // 于是"漏传权重"会伪装成"权重为 0",报错信息也会变成莫名其妙的"权重需在 1~5 之间"。
            @NotNull(message = "权重不能为空")
            @Min(value = UserInterest.MIN_WEIGHT, message = "权重需在 1~5 之间")
            @Max(value = UserInterest.MAX_WEIGHT, message = "权重需在 1~5 之间")
            Integer weight) {
    }
}
