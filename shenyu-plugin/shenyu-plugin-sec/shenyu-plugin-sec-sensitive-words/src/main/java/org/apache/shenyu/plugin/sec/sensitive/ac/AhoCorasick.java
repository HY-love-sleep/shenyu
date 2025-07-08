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
}
