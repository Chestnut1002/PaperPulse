package com.paperpulse.interest;

import com.paperpulse.common.ApiException;
import com.paperpulse.interest.dto.InterestCatalogResponse;
import com.paperpulse.interest.dto.UpdateInterestsRequest;
import com.paperpulse.interest.dto.UserInterestResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 兴趣标签业务逻辑。词表本身是静态的({@link InterestTag}),这里只管用户的选取。 */
@Service
public class InterestService {

    /**
     * 一个用户最多能选多少个标签。
     *
     * <p>限制不是怕存不下,而是怕失去区分度:标签选得越多,每个标签能贡献的信号越弱,
     * 最后等于没填。十个足够覆盖一个人的研究方向。
     */
    public static final int MAX_TAGS = 10;

    /**
     * 按词表声明顺序排 —— 分类在枚举里是成块声明的,排完自然就是按分类分组的效果。
     *
     * <p>走 {@link InterestTag#findByKey} 而不是拿 key 反推枚举名:后者等于把
     * "key 就是小写枚举名"这条实现细节复制到第二处,还踩了 {@code toUpperCase()} 不带 Locale 的坑。
     * 查不到就给个最大值排到最后,不抛异常 —— 排序不该成为第二个失败点。
     */
    private static final Comparator<UserInterestResponse> BY_VOCABULARY_ORDER =
            Comparator.comparingInt(response -> InterestTag.findByKey(response.tag())
                    .map(InterestTag::ordinal)
                    .orElse(Integer.MAX_VALUE));

    private final UserInterestRepository userInterestRepository;

    public InterestService(UserInterestRepository userInterestRepository) {
        this.userInterestRepository = userInterestRepository;
    }

    /** 标签词表。不含任何用户数据,登录后即可取。 */
    public InterestCatalogResponse catalog() {
        return InterestCatalogResponse.from(InterestTag.groupedByCategory(), MAX_TAGS);
    }

    /**
     * 读取某用户的兴趣。
     *
     * <p>库里可能残留词表中已下线的 key(标签被移除过),这里按词表过滤掉 ——
     * 让它们在读取时静默消失,好过让接口返回一个前端根本不认识的标签。
     */
    @Transactional(readOnly = true)
    public List<UserInterestResponse> listFor(Long userId) {
        return userInterestRepository.findByUserId(userId).stream()
                .map(entity -> InterestTag.findByKey(entity.getTagKey())
                        .map(tag -> UserInterestResponse.of(entity, tag)))
                .flatMap(Optional::stream)
                .sorted(BY_VOCABULARY_ORDER)
                .toList();
    }

    /**
     * 全量替换某用户的兴趣,返回替换后的结果。
     *
     * <p>用"先清空再写入"而不是逐条比对差异:标签最多十个,这点开销可以忽略,
     * 换来的是语义极其简单 —— 请求体就是最终状态,不需要推理增删改哪条路径出了问题。
     *
     * <p>整个过程在一个事务里,中途失败会整体回滚,不会出现"旧的删了、新的没进去"。
     */
    @Transactional
    public List<UserInterestResponse> replaceFor(Long userId, List<UpdateInterestsRequest.Item> items) {
        // 词表校验放在删数据之前:参数不合法时连旧数据都不该动
        Set<String> seen = new HashSet<>();
        for (UpdateInterestsRequest.Item item : items) {
            String key = item.tag();
            InterestTag tag = InterestTag.fromKey(key);
            if (!seen.add(tag.key())) {
                throw ApiException.badRequest("标签重复:" + tag.displayName());
            }
        }

        userInterestRepository.deleteAllByUserId(userId);

        List<UserInterest> saved = userInterestRepository.saveAll(items.stream()
                .map(item -> new UserInterest(userId, item.tag(), item.weight()))
                .toList());

        return saved.stream()
                .map(entity -> UserInterestResponse.of(entity, InterestTag.fromKey(entity.getTagKey())))
                .sorted(BY_VOCABULARY_ORDER)
                .toList();
    }
}
