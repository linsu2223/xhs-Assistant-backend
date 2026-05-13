package com.xhs.rewriter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhs.rewriter.domain.Note;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class XhsFetchService {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]*(xiaohongshu\\.com/(discovery/item|explore)/)[A-Za-z0-9]{16,32}[^\\s]*");
    private static final Pattern TOPIC_PATTERN = Pattern.compile("#[\\p{IsHan}A-Za-z0-9_\\-]+");
    private static final Pattern JSON_URL_PATTERN = Pattern.compile("https?:\\\\?/\\\\?/[^\"'<>\\s]+");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public XhsFetchService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Note fetchByUrl(String url, String cookie) {
        if (!StringUtils.hasText(cookie)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置小红书Cookie");
        }
        String normalizedUrl = normalizeUrl(url);
        String html = fetchHtml(normalizedUrl, cookie);
        Note note = parseNote(normalizedUrl, html);
        if ("未获取到标题".equals(note.getTitle()) && note.getOriginalContent().startsWith("未获取到正文内容")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "未能从页面解析到笔记信息。可能是Cookie失效、页面需要验证，或平台未返回可解析内容。");
        }
        return note;
    }

    private String normalizeUrl(String url) {
        if (!StringUtils.hasText(url) || !URL_PATTERN.matcher(url.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入正确的小红书笔记链接");
        }
        return url.trim();
    }

    private String fetchHtml(String url, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, cookie.trim());
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        headers.set(HttpHeaders.REFERER, "https://www.xiaohongshu.com/");
        headers.set(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.setAcceptLanguageAsLocales(java.util.Collections.singletonList(java.util.Locale.CHINA));
        headers.setAccept(java.util.Collections.singletonList(MediaType.TEXT_HTML));

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "小红书页面未返回可解析内容");
            }
            return response.getBody();
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "请求小红书页面失败，请确认链接和Cookie是否有效");
        }
    }

    private Note parseNote(String url, String html) {
        Note note = new Note();
        String fetchedAt = LocalDateTime.now().format(DATE_TIME_FORMATTER);
        String plainText = stripTags(html);
        ParsedNote structured = parseStructuredNote(html);
        String title = firstNonBlank(
                structured.title,
                meta(html, "og:title"),
                meta(html, "twitter:title"),
                findJsonText(html, "noteTitle", "displayTitle", "title"),
                titleTag(html)
        );
        String content = firstNonBlank(
                structured.content,
                meta(html, "og:description"),
                findJsonText(html, "noteContent", "content", "desc", "description"),
                meta(html, "description")
        );

        title = cleanTitle(title);
        content = cleanText(content);

        List<String> topics = structured.topics.isEmpty() ? findTopics(firstNonBlank(content, plainText)) : structured.topics;
        List<String> images = structured.images.isEmpty() ? findImages(html) : structured.images;
        String authorName = firstNonBlank(
                structured.authorName,
                meta(html, "author")
        );
        String authorSignature = firstNonBlank(structured.authorSignature, "无");
        String publishTime = firstNonBlank(structured.publishTime, findJsonText(html, "publishTime", "createTime", "timestamp"), "未获取到");
        String lastUpdateTime = firstNonBlank(structured.lastUpdateTime, findJsonText(html, "lastUpdateTime", "updateTime", "editTime"), publishTime);

        note.setNoteUrl(url);
        note.setFetchedAt(fetchedAt);
        note.setTitle(firstNonBlank(title, "未获取到标题"));
        note.setOriginalContent(firstNonBlank(content, "未获取到正文内容，请检查Cookie有效性或改用手动录入。"));
        note.setTagsJson(toJson(topics));
        note.setAuthorName(firstNonBlank(authorName, "未获取到"));
        note.setAuthorSignature(firstNonBlank(authorSignature, "无"));
        note.setPublishTime(normalizeTime(publishTime));
        note.setLastUpdateTime(normalizeTime(lastUpdateTime));
        note.setCollectCount(firstCount(structured.collectCount, findCount(html, "collectCount", "collectedCount", "collectNum", "收藏")));
        note.setLikeCount(firstCount(structured.likeCount, findCount(html, "likedCount", "likeCount", "likes", "点赞")));
        note.setShareCount(firstCount(structured.shareCount, findCount(html, "shareCount", "shareNum", "转发", "分享")));
        note.setCommentCount(firstCount(structured.commentCount, findCount(html, "commentCount", "comments", "评论")));
        note.setImageUrlsJson(toJson(images));
        return note;
    }

    private ParsedNote parseStructuredNote(String html) {
        ParsedNote result = new ParsedNote();
        for (JsonNode root : extractJsonRoots(html)) {
            JsonNode noteNode = findBestNoteNode(root);
            if (noteNode != null) {
                fillFromNoteNode(result, noteNode);
            }
            if (StringUtils.hasText(result.title) && StringUtils.hasText(result.content)) {
                break;
            }
        }
        return result;
    }

    private List<JsonNode> extractJsonRoots(String html) {
        List<JsonNode> roots = new ArrayList<>();
        String[] markers = {"window.__INITIAL_STATE__", "__INITIAL_STATE__", "__APOLLO_STATE__", "__NEXT_DATA__"};
        for (String marker : markers) {
            int index = html.indexOf(marker);
            while (index >= 0) {
                int start = html.indexOf('{', index);
                if (start < 0) {
                    break;
                }
                String json = readBalancedObject(html, start);
                if (StringUtils.hasText(json)) {
                    JsonNode node = parseJsonObject(json);
                    if (node != null) {
                        roots.add(node);
                    }
                }
                index = html.indexOf(marker, index + marker.length());
            }
        }
        return roots;
    }

    private String readBalancedObject(String text, int start) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    inString = false;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                inString = true;
                quote = ch;
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return "";
    }

    private JsonNode parseJsonObject(String rawJson) {
        String json = rawJson.replaceAll(":\\s*undefined", ":null");
        try {
            return objectMapper.readTree(json);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode findBestNoteNode(JsonNode root) {
        JsonNode fromDetailMap = findFromNoteDetailMap(root);
        if (fromDetailMap != null) {
            return fromDetailMap;
        }
        List<JsonNode> candidates = new ArrayList<>();
        collectNoteCandidates(root, candidates);
        JsonNode best = null;
        int bestScore = 0;
        for (JsonNode candidate : candidates) {
            int score = noteScore(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore >= 4 ? best : null;
    }

    private JsonNode findFromNoteDetailMap(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        if (node.isObject() && node.has("noteDetailMap")) {
            JsonNode map = node.get("noteDetailMap");
            if (map != null && map.isObject()) {
                java.util.Iterator<JsonNode> values = map.elements();
                while (values.hasNext()) {
                    JsonNode value = values.next();
                    JsonNode note = firstExisting(value, "note", "noteInfo");
                    if (note != null && note.isObject()) {
                        return value;
                    }
                    if (value.isObject()) {
                        return value;
                    }
                }
            }
        }
        if (node.isContainerNode()) {
            java.util.Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                JsonNode found = findFromNoteDetailMap(children.next());
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void collectNoteCandidates(JsonNode node, List<JsonNode> candidates) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            if (noteScore(node) > 0) {
                candidates.add(node);
            }
            java.util.Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                collectNoteCandidates(children.next(), candidates);
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectNoteCandidates(item, candidates);
            }
        }
    }

    private int noteScore(JsonNode node) {
        int score = 0;
        if (hasAny(node, "noteId", "note_id", "id")) score += 1;
        if (hasTextAny(node, "title", "displayTitle", "noteTitle")) score += 2;
        if (hasTextAny(node, "desc", "content", "noteContent", "description")) score += 2;
        if (firstExisting(node, "user", "userInfo", "author") != null) score += 2;
        if (firstExisting(node, "interactInfo", "interactionInfo", "stat", "stats") != null) score += 1;
        if (firstExisting(node, "tagList", "hashTag", "topics") != null) score += 1;
        return score;
    }

    private void fillFromNoteNode(ParsedNote result, JsonNode noteNode) {
        JsonNode noteData = firstNonNull(firstExisting(noteNode, "note", "noteInfo"), noteNode);
        result.title = firstNonBlank(result.title, text(noteData, "title", "displayTitle", "noteTitle"));
        result.content = firstNonBlank(result.content, text(noteData, "desc", "content", "noteContent", "description"));
        addTopics(result.topics, firstExisting(noteData, "tagList", "hashTag", "topics"));
        addImages(result.images, firstExisting(noteData, "imageList", "images", "imageInfoList"));
        JsonNode video = firstExisting(noteData, "video", "videoInfo");
        if (video != null) {
            addImageFromNode(result.images, firstExisting(video, "cover", "image", "thumbnail"));
        }

        JsonNode user = firstNonNull(
                firstExisting(noteData, "user", "userInfo", "author", "authorInfo"),
                firstExisting(noteNode, "user", "userInfo", "author", "authorInfo")
        );
        if (user != null) {
            result.authorName = firstNonBlank(result.authorName, text(user, "nickname", "nickName", "name", "userName"));
            result.authorSignature = firstNonBlank(result.authorSignature, text(user, "desc", "signature", "userDesc", "description"));
        }

        JsonNode interact = firstNonNull(
                firstExisting(noteData, "interactInfo", "interactionInfo", "stat", "stats"),
                firstExisting(noteNode, "interactInfo", "interactionInfo", "stat", "stats")
        );
        result.likeCount = firstCount(result.likeCount, number(interact, "likedCount", "likeCount", "likes"));
        result.collectCount = firstCount(result.collectCount, number(interact, "collectedCount", "collectCount", "collectNum"));
        result.shareCount = firstCount(result.shareCount, number(interact, "shareCount", "shareNum", "sharedCount"));
        result.commentCount = firstCount(result.commentCount, number(interact, "commentCount", "comments", "commentNum"));
        result.publishTime = firstNonBlank(result.publishTime, text(noteData, "time", "publishTime", "createTime", "timestamp"));
        result.lastUpdateTime = firstNonBlank(result.lastUpdateTime, text(noteData, "lastUpdateTime", "updateTime", "editTime"));
    }

    private void addTopics(List<String> topics, JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String topic = firstNonBlank(text(item, "name", "tagName", "title"), item.isTextual() ? item.asText() : "");
                if (StringUtils.hasText(topic)) {
                    topics.add(topic.startsWith("#") ? topic : "#" + topic);
                }
            }
        } else if (node.isTextual()) {
            topics.addAll(findTopics(node.asText()));
        }
    }

    private void addImages(List<String> images, JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                addImageFromNode(images, item);
            }
        } else {
            addImageFromNode(images, node);
        }
    }

    private void addImageFromNode(List<String> images, JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return;
        }
        String url = firstNonBlank(
                text(node, "url", "src", "traceId", "fileId"),
                text(firstExisting(node, "infoList"), "url"),
                node.isTextual() ? node.asText() : ""
        );
        if (StringUtils.hasText(url)) {
            Set<String> holder = new LinkedHashSet<>(images);
            addImage(holder, url);
            images.clear();
            images.addAll(holder);
        }
    }

    private JsonNode firstExisting(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstNonNull(JsonNode first, JsonNode second) {
        return first != null && !first.isNull() && !first.isMissingNode() ? first : second;
    }

    private boolean hasAny(JsonNode node, String... keys) {
        return firstExisting(node, keys) != null;
    }

    private boolean hasTextAny(JsonNode node, String... keys) {
        return StringUtils.hasText(text(node, keys));
    }

    private String text(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (keys.length == 0) {
            return node.isValueNode() ? node.asText() : "";
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = text(item, keys);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            return "";
        }
        if (!node.isObject()) {
            return "";
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isValueNode()) {
                String text = cleanText(value.asText());
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
            if (value.isArray()) {
                String text = text(value, keys);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private Integer number(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asInt();
            }
            if (value.isTextual()) {
                return parseCount(value.asText());
            }
        }
        return null;
    }

    private Integer firstCount(Integer preferred, Integer fallback) {
        return preferred != null ? preferred : (fallback == null ? 0 : fallback);
    }

    private String meta(String html, String name) {
        String quotedName = Pattern.quote(name);
        String[] patterns = {
                "<meta[^>]+property=[\"']" + quotedName + "[\"'][^>]+content=[\"']([^\"']*)[\"'][^>]*>",
                "<meta[^>]+name=[\"']" + quotedName + "[\"'][^>]+content=[\"']([^\"']*)[\"'][^>]*>",
                "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+property=[\"']" + quotedName + "[\"'][^>]*>",
                "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+name=[\"']" + quotedName + "[\"'][^>]*>"
        };
        for (String pattern : patterns) {
            Matcher matcher = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(html);
            if (matcher.find()) {
                return decodeHtml(matcher.group(1));
            }
        }
        return "";
    }

    private String titleTag(String html) {
        Matcher matcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        return matcher.find() ? decodeHtml(matcher.group(1)) : "";
    }

    private String findJsonText(String html, String... keys) {
        for (String key : keys) {
            String value = findJsonTextByKey(html, key);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String findJsonTextByKey(String html, String key) {
        String quotedKey = Pattern.quote(key);
        Pattern pattern = Pattern.compile("[\"']" + quotedKey + "[\"']\\s*:\\s*[\"']((?:\\\\.|[^\"'\\\\])*)[\"']", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(html);
        while (matcher.find()) {
            String value = cleanText(unescapeJson(matcher.group(1)));
            if (StringUtils.hasText(value) && value.length() < 5000 && !looksLikeUrl(value)) {
                return value;
            }
        }
        return "";
    }

    private Integer findCount(String html, String... keys) {
        for (String key : keys) {
            Integer value = findNumericValue(html, key);
            if (value != null) {
                return value;
            }
        }
        return 0;
    }

    private Integer findNumericValue(String html, String key) {
        String quotedKey = Pattern.quote(key);
        Pattern jsonNumber = Pattern.compile("[\"']" + quotedKey + "[\"']\\s*:\\s*[\"']?([0-9]+(?:\\.[0-9]+)?[万wWkK]?)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = jsonNumber.matcher(html);
        if (matcher.find()) {
            return parseCount(matcher.group(1));
        }

        Pattern labelNumber = Pattern.compile(Pattern.quote(key) + "\\s*[:：]?\\s*([0-9]+(?:\\.[0-9]+)?[万wWkK]?)", Pattern.CASE_INSENSITIVE);
        matcher = labelNumber.matcher(stripTags(html));
        return matcher.find() ? parseCount(matcher.group(1)) : null;
    }

    private Integer parseCount(String raw) {
        if (!StringUtils.hasText(raw)) {
            return 0;
        }
        String value = raw.trim();
        try {
            double number = Double.parseDouble(value.replaceAll("[万wWkK]", ""));
            if (value.contains("万") || value.contains("w") || value.contains("W")) {
                return (int) Math.round(number * 10000);
            }
            if (value.contains("k") || value.contains("K")) {
                return (int) Math.round(number * 1000);
            }
            return (int) Math.round(number);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<String> findTopics(String text) {
        Set<String> topics = new LinkedHashSet<>();
        Matcher matcher = TOPIC_PATTERN.matcher(text);
        while (matcher.find()) {
            topics.add(matcher.group());
        }
        return new ArrayList<>(topics);
    }

    private List<String> findImages(String html) {
        Set<String> images = new LinkedHashSet<>();
        addImage(images, meta(html, "og:image"));
        addImage(images, meta(html, "twitter:image"));

        Matcher matcher = JSON_URL_PATTERN.matcher(html);
        while (matcher.find() && images.size() < 20) {
            String url = unescapeJson(matcher.group());
            if (isImageUrl(url)) {
                addImage(images, url);
            }
        }
        return new ArrayList<>(images);
    }

    private void addImage(Set<String> images, String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return;
        }
        String url = rawUrl.replace("\\/", "/").trim();
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (looksLikeUrl(url)) {
            images.add(url);
        }
    }

    private boolean isImageUrl(String url) {
        String lower = url.toLowerCase();
        return looksLikeUrl(url)
                && (lower.contains("image") || lower.contains("xhscdn") || lower.contains("sns-webpic") || lower.matches(".*\\.(jpg|jpeg|png|webp)(\\?.*)?$"));
    }

    private String normalizeTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未获取到";
        }
        String value = raw.trim();
        if (value.matches("\\d{13}")) {
            return java.time.Instant.ofEpochMilli(Long.parseLong(value))
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DATE_TIME_FORMATTER);
        }
        if (value.matches("\\d{10}")) {
            return java.time.Instant.ofEpochSecond(Long.parseLong(value))
                    .atZone(java.time.ZoneId.systemDefault())
                    .format(DATE_TIME_FORMATTER);
        }
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private String cleanTitle(String value) {
        String title = cleanText(value);
        title = title.replaceAll("\\s*-\\s*小红书.*$", "");
        return title;
    }

    private String cleanText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String decoded = decodeHtml(value);
        decoded = decoded.replace("\\n", "\n").replace("\\r", "\n").replace("\\t", " ");
        decoded = decoded.replaceAll("[ \\u00A0]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return decoded;
    }

    private String stripTags(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return decodeHtml(html.replaceAll("<script[\\s\\S]*?</script>", " ")
                .replaceAll("<style[\\s\\S]*?</style>", " ")
                .replaceAll("<[^>]+>", " "));
    }

    private String decodeHtml(String value) {
        if (value == null) {
            return "";
        }
        String decoded = value.replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
        try {
            return URLDecoder.decode(decoded, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return decoded;
        }
    }

    private String unescapeJson(String value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.readValue("\"" + value.replace("\"", "\\\"") + "\"", String.class);
        } catch (Exception ignored) {
            return value.replace("\\/", "/").replace("\\\"", "\"");
        }
    }

    private boolean looksLikeUrl(String value) {
        return StringUtils.hasText(value) && (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("//"));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON 序列化失败", ex);
        }
    }

    private static class ParsedNote {
        private String title;
        private String content;
        private String authorName;
        private String authorSignature;
        private String publishTime;
        private String lastUpdateTime;
        private Integer likeCount;
        private Integer collectCount;
        private Integer shareCount;
        private Integer commentCount;
        private final List<String> topics = new ArrayList<>();
        private final List<String> images = new ArrayList<>();
    }
}
