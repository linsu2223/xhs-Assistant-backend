package com.xhs.rewriter.service;

import com.xhs.rewriter.domain.Note;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

@Service
public class XhsFetchService {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]*(xiaohongshu\\.com/(discovery/item|explore)/)[A-Za-z0-9]{16,32}[^\\s]*");

    public Note fetchByUrl(String url, String cookie) {
        if (cookie == null || cookie.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先配置小红书Cookie");
        }
        if (url == null || !URL_PATTERN.matcher(url.trim()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入正确的小红书笔记链接");
        }

        Note note = new Note();
        note.setNoteUrl(url.trim());
        note.setTitle("小红书笔记拆解样例");
        note.setOriginalContent("这是一条通过链接获取后的笔记内容示例。真实接入时，服务会使用当前用户配置的 Cookie 请求小红书页面或接口，解析标题、正文、图片、互动数据和标签。当前版本先保留完整流程，方便验证分析与重写体验。");
        note.setImageUrlsJson("[\"https://images.unsplash.com/photo-1512496015851-a90fb38ba796\"]");
        note.setLikeCount(1280);
        note.setCollectCount(734);
        note.setCommentCount(96);
        note.setAuthorName("内容创作者");
        note.setPublishTime(LocalDateTime.now().minusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        note.setTagsJson("[\"#种草\",\"#内容运营\",\"#小红书\"]");
        return note;
    }
}
