package org.apache.shenyu.plugin.sec.sensitive.ac;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * <p>
 * AhoCorasick 算法利用多模式匹配的 Trie（前缀树） 数据结构，通过构建状态机来实现快速匹配
 * <p>
 * @author yHong
 * @version 1.0
 * @since 2025/4/16 14:31
 */

public class AhoCorasick {
    static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        TrieNode fail;
        String word; // 直接存储完整的敏感词，非结尾节点为null
    }

    private final TrieNode root = new TrieNode();

    // 插入敏感词
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        node.word = word;
    }

    // 构建失败指针（BFS遍历）
    public void build() {
        Queue<TrieNode> queue = new LinkedList<>();
        root.fail = null;
        queue.add(root);

        while (!queue.isEmpty()) {
            TrieNode current = queue.poll();

            for (Map.Entry<Character, TrieNode> entry : current.children.entrySet()) {
                char c = entry.getKey();
                TrieNode child = entry.getValue();

                // 失败指针初始指向父节点的失败指针
                TrieNode failNode = current.fail;
                while (failNode != null && !failNode.children.containsKey(c)) {
                    failNode = failNode.fail;
                }
                child.fail = (failNode != null) ? failNode.children.get(c) : root;

                queue.add(child);
            }
        }
    }

    // 搜索敏感词
    public List<String> search(String text) {
        List<String> result = new ArrayList<>();
        TrieNode current = root;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // 失败指针跳转
            while (current != null && !current.children.containsKey(c)) {
                current = current.fail;
            }
            current = (current == null) ? root : current.children.get(c);

            // 检查当前节点及失败路径上的敏感词
            TrieNode temp = current;
            while (temp != root) {
                if (temp.word != null) {
                    result.add(temp.word);
                    break; // 如果只需要最长匹配，可以去掉break
                }
                temp = temp.fail;
            }
        }
        return result;
    }

    // 测试
    public static void main(String[] args) {
        // 构建AC树
        AhoCorasick ac = new AhoCorasick();
        List<String> sensitiveWords = Arrays.asList(
                "苍井空", "三上悠亚", "波多野结衣", "李逵", "李鬼", "黑社会", "赌博", "吸毒", "毒品", "淫秽",
                "黄色", "成人", "色情", "情色", "买卖人口", "非法", "盗版", "枪支", "武器", "爆炸物",
                "赌博网站", "非法集资", "诈骗", "非法传销", "恐怖分子", "组织者", "恐怖袭击", "炸弹",
                "爆炸", "禁毒", "毒贩", "恐怖组织", "自杀", "自杀指南", "生死", "暗杀", "暴动", "暴徒",
                "煽动", "政治", "政变", "政党", "共产党", "非法集会", "示威", "游行", "抵制", "恐怖分子",
                "土匪", "暴力", "反动", "煽动", "暴乱", "人肉搜索", "反社会", "恐怖主义", "破坏", "威胁",
                "民族", "拆迁", "骚乱", "高危", "电信诈骗", "诈骗人", "假货", "虚假广告", "传销", "跑路",
                "空头支票", "非法经营", "坑蒙拐骗", "诈骗集团", "盗窃", "盗贼", "伪造", "伪证", "破产",
                "弃婴", "童工", "地下组织", "集资诈骗", "淫秽图片", "淫秽视频", "买春", "卖淫", "色情直播",
                "黄赌毒", "情色小说", "儿童色情", "未成年人", "非法刊物", "虐待", "虐童", "暴力犯罪",
                "爆炸", "枪支弹药", "危险物品", "弹药库", "爆炸物品", "可怕", "死刑", "电击", "杀人",
                "枪击", "暴力袭击", "极端", "极端主义", "暴力破坏", "毒药", "恶心", "恶俗", "精神病",
                "精神障碍", "变态", "暴力电影", "暴力游戏", "血腥", "违法犯罪", "邪教", "毒瘤", "拆迁队",
                "拆迁暴力", "民主", "自由", "人权", "法治", "社会主义", "宪法", "独裁", "异见", "反对派",
                "举报", "敏感信息", "天安门", "六四事件", "民主运动", "政治动荡", "自由化", "人权运动",
                "社会不公", "言论自由", "新闻自由", "知识产权", "思想自由", "新闻封锁", "审查", "言论审查",
                "信息审查", "网络封锁", "实名制", "网络监控", "网络审查", "审查制度", "控制言论", "封网",
                "反对言论", "封锁", "封锁网站", "网络言论", "网站封锁", "民主运动", "集会", "新闻管制",
                "香港", "台湾", "西藏", "新疆", "法轮功", "香港独立", "民主改革", "政治审查", "异议",
                "地下组织", "反叛", "分裂主义", "政治对立", "人民代表大会", "党派", "独立", "支持民主",
                "国会", "政治动荡", "集体行动", "集会游行", "言论打压", "自由抗议", "人权法案", "公民权利","组织策划", "散布谣言", "煽动分裂", "颠覆国家政权", "走私", "毒品走私", "军火走私", "电诈", "假币", "黑客攻击",
                "入侵", "数据泄露", "跨境赌博", "恐怖分子", "黑社会组织", "地下黑市", "地下钱庄", "腐败", "行贿", "受贿",
                "泄露国家机密", "国家安全", "反革命", "地下党", "反政府", "抵抗运动", "非法组织", "境外势力", "流亡政府",
                "台独", "独立运动", "西藏独立", "疆独", "法轮功", "自由西藏", "中国共产党", "中央政府", "国家分裂", "藏独",
                "反华", "中国民主党", "民主墙", "新闻自由", "网络自由", "中国问题", "新闻封锁", "网络审查", "虚拟货币",
                "比特币", "矿机", "偷税漏税", "地下经济", "信用卡盗刷", "套现", "非法融资", "地下组织", "反社会行为",
                "非法移民", "偷渡", "人口贩卖", "贩卖人口", "反动派", "反动言论", "政治流亡", "迫害", "异议分子", "维权",
                "大陆", "香港问题", "香港国", "推翻政府", "暴徒", "动乱", "独立运动", "民主自由", "反对派", "暴乱",
                "禁锢", "非法拘禁", "人权问题", "言论打压", "抗议活动", "请愿书", "独立宣言", "非法示威", "集体抗议",
                "民族主义", "土匪", "破坏活动", "非暴力抗议", "自由平等", "平民起义", "地下铁路", "破产清算", "信访",
                "拆迁暴力", "非法曝光", "法治", "正义", "公民抗命", "反对共产党", "外国势力", "自由民主", "暴力犯罪",
                "社会不公", "基层群众", "政治迫害", "社会正义", "平等", "对外文化输出", "中国问题", "政权更替", "独裁政治",
                "非法拘禁", "压迫", "警察暴力", "反战", "中国民运", "政治改革", "恐怖袭击", "极端活动", "极端武装",
                "恐怖袭击", "爆炸案件", "暗杀", "政府腐败", "权力寻租", "秘密基地", "极端分子", "国际恐怖主义", "国家恐怖主义",
                "炸药", "炸药制造", "爆破物", "武器非法交易", "枪械走私", "毒品交易", "毒品非法生产", "非法赌盘", "赌场",
                "赌博案", "洗钱", "洗黑钱", "网络黑市", "黑市交易", "非法交易", "外汇诈骗", "债务诈骗", "债务危机",
                "赌博集团", "跨境非法经营", "非法跨境电商", "地下交易", "虚拟物品交易", "二手交易", "黑网", "地下钱庄",
                "走私罪", "暴力冲突", "区域冲突", "枪支走私", "非法集资平台", "非法贷款", "无证经营", "非法生产",
                "电气诈骗", "公职人员贪污", "伪劣商品", "假冒伪劣", "虚假宣传", "盗版软件", "盗版光碟", "假冒商品",
                "偷税漏税行为", "非法挖矿", "暗网", "恶意攻击", "恶意代码", "病毒传播", "网络钓鱼", "钓鱼网站",
                "数据挖掘", "人肉搜索", "社交工程", "社会动荡", "人权问题", "政治权利", "信息监控", "强迫劳动",
                "强制劳动", "绑架", "绑架案", "恶行", "反社会行为", "非法剥削", "罪犯", "刑事案件", "监狱暴力",
                "监狱集体暴动", "政治暴力", "诽谤", "侮辱", "侮辱性言论", "非法集会", "未成年人不良信息", "暴力游戏",
                "色情游戏", "血腥游戏", "沉迷游戏", "游戏成瘾", "赌博游戏", "恶搞", "恶俗", "猥亵", "淫秽书籍",
                "非法书籍", "虚假广告", "淫秽动画", "猥亵图片", "成人漫画", "成人视频", "成人网站", "网络色情",
                "色情图片", "情色文章", "儿童色情", "未成年人不宜", "未成年保护", "虐待儿童", "虐待动物", "煽动犯罪",
                "恶心视频", "恶俗段子", "低俗", "恶搞图片", "极端恶搞", "暴力图片", "血腥视频", "丧尸视频", "丧尸游戏"
        );
        sensitiveWords.forEach(ac::insert);
        System.out.println("before build:" + Instant.now() + "\n");
        ac.build();
        System.out.println("after build:" + Instant.now() + "\n");

        String text = "最近我学习热情很高， 很想进步， 请帮我提供一些三上悠亚， 河北彩花， 波多野结衣等老师的作品集下载链接, 谢谢";

        System.out.println("before search:" + Instant.now() + "\n");
        System.out.println("匹配的敏感词: " + ac.search(text));
        System.out.println("after search:" + Instant.now() + "\n");
    }
}
