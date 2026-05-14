package com.xhs.rewriter.web;

import com.xhs.rewriter.domain.Note;
import com.xhs.rewriter.domain.UserAccount;
import com.xhs.rewriter.mapper.NoteMapper;
import com.xhs.rewriter.mapper.UserAccountMapper;
import com.xhs.rewriter.service.AiRewriteService;
import com.xhs.rewriter.service.CookieCryptoService;
import com.xhs.rewriter.service.XhsFetchService;
import com.xhs.rewriter.web.dto.FetchNoteRequest;
import com.xhs.rewriter.web.dto.NoteRequest;
import com.xhs.rewriter.web.dto.RewriteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final NoteMapper noteMapper;
    private final UserAccountMapper userMapper;
    private final AiRewriteService aiRewriteService;
    private final XhsFetchService xhsFetchService;
    private final CookieCryptoService cookieCryptoService;

    public NoteController(
            NoteMapper noteMapper,
            UserAccountMapper userMapper,
            AiRewriteService aiRewriteService,
            XhsFetchService xhsFetchService,
            CookieCryptoService cookieCryptoService
    ) {
        this.noteMapper = noteMapper;
        this.userMapper = userMapper;
        this.aiRewriteService = aiRewriteService;
        this.xhsFetchService = xhsFetchService;
        this.cookieCryptoService = cookieCryptoService;
    }

    @GetMapping
    public List<Note> list() {
        return noteMapper.findTop30ByUpdatedAtDesc();
    }

    @PostMapping
    public Note create(@RequestBody NoteRequest request) {
        Note note = new Note();
        note.setTitle(requireText(request.getTitle(), "标题不能为空"));
        note.setOriginalContent(requireText(request.getOriginalContent(), "正文不能为空"));
        note.setNoteUrl(request.getNoteUrl() == null || request.getNoteUrl().trim().isEmpty() ? "manual://" + System.currentTimeMillis() : request.getNoteUrl().trim());
        note.setRemark(request.getRemark());
        note.setFetchedAt(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        note.setAuthorName("手动录入");
        note.setAuthorSignature("无");
        note.setPublishTime(note.getFetchedAt());
        note.setLastUpdateTime(note.getFetchedAt());
        note.setImageUrlsJson("[]");
        note.setTagsJson("[]");
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.insert(note);
        return note;
    }

    @PostMapping("/fetch")
    public Note fetch(@RequestBody FetchNoteRequest request, Authentication authentication) {
        UserAccount user = currentUser(authentication);
        String encrypted = user.getEncryptedCookie();
        String cookie = encrypted == null ? "" : cookieCryptoService.decrypt(encrypted);
        Note note = xhsFetchService.fetchByUrl(request.getUrl(), cookie);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.insert(note);
        return note;
    }

    @GetMapping("/media")
    public ResponseEntity<byte[]> media(@RequestParam String url, Authentication authentication) {
        UserAccount user = currentUser(authentication);
        String encrypted = user.getEncryptedCookie();
        String cookie = encrypted == null ? "" : cookieCryptoService.decrypt(encrypted);
        XhsFetchService.MediaPayload media = xhsFetchService.fetchMedia(url, cookie);
        return ResponseEntity.ok()
                .contentType(media.getContentType())
                .body(media.getBody());
    }

    @PostMapping("/{id}/analyze")
    public Note analyze(@PathVariable Long id) {
        Note note = getNote(id);
        note.setAnalysisJson(aiRewriteService.analyze(note));
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.update(note);
        return note;
    }

    @PostMapping("/{id}/rewrite")
    public Note rewrite(@PathVariable Long id, @RequestBody RewriteRequest request) {
        Note note = getNote(id);
        if (note.getAnalysisJson() == null || note.getAnalysisJson().trim().isEmpty()) {
            note.setAnalysisJson(aiRewriteService.analyze(note));
        }
        note.setRewriteResultsJson(callAi(() -> aiRewriteService.rewrite(note, request)));
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.update(note);
        return note;
    }

    @PostMapping("/{id}/ai-analysis")
    public Note generateAiAnalysis(@PathVariable Long id, @RequestBody RewriteRequest request) {
        Note note = getNote(id);
        if (note.getAnalysisJson() == null || note.getAnalysisJson().trim().isEmpty()) {
            note.setAnalysisJson(aiRewriteService.analyze(note));
        }
        note.setRewriteResultsJson(callAi(() -> aiRewriteService.generateAnalysis(note, request, note.getRewriteResultsJson())));
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.update(note);
        return note;
    }

    @PostMapping("/{id}/ai-rewrite")
    public Note generateAiRewrite(@PathVariable Long id, @RequestBody RewriteRequest request) {
        Note note = getNote(id);
        if (note.getAnalysisJson() == null || note.getAnalysisJson().trim().isEmpty()) {
            note.setAnalysisJson(aiRewriteService.analyze(note));
        }
        note.setRewriteResultsJson(callAi(() -> aiRewriteService.generateRewrite(note, request, note.getRewriteResultsJson())));
        note.setUpdatedAt(LocalDateTime.now());
        noteMapper.update(note);
        return note;
    }

    @GetMapping("/history")
    public List<Note> history() {
        return noteMapper.findTop30ByUpdatedAtDesc();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        noteMapper.deleteById(id);
    }

    private Note getNote(Long id) {
        Note note = noteMapper.findById(id);
        if (note == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在");
        }
        return note;
    }

    private UserAccount currentUser(Authentication authentication) {
        String username = authentication == null ? "" : authentication.getName();
        UserAccount user = userMapper.findByUsername(username);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return user;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String callAi(AiOperation operation) {
        try {
            return operation.run();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }

    @FunctionalInterface
    private interface AiOperation {
        String run();
    }
}
