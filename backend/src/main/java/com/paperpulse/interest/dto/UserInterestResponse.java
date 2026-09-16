package com.paperpulse.interest.dto;

import com.paperpulse.interest.InterestTag;
import com.paperpulse.interest.UserInterest;

/**
 * 用户的一个兴趣标签。
 *
 * <p>展示名与分类是**查词表得来**的,不存库 —— 这样以后改措辞或调整分类,
 * 老数据不用迁移,读出来自动就是新的。
 *
 * @param tag         标签 key,前端回传时用它
 * @param displayName 展示名
 * @param category    所属分类
 * @param weight      兴趣强度 1~5
 */
public record UserInterestResponse(String tag, String displayName, String category, int weight) {

    public static UserInterestResponse of(UserInterest entity, InterestTag tag) {
        return new UserInterestResponse(tag.key(), tag.displayName(), tag.category(), entity.getWeight());
    }
}
