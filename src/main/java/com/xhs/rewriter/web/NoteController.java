package com.xhs.rewriter.web;

import com.xhs.rewriter.domain.Note;
import com.xhs.rewriter.domain.UserAccount;
import com.xhs.rewriter.repository.NoteRepository;
import com.xhs.rewriter.repository.UserAccountRepository;
import com.xhs.rewriter.service.AiRewriteService;
import com.xhs.rewriter.service.CookieCryptoService;
import com.xhs.rewriter.service.XhsFetchService;
import com.xhs.rewriter.web.dto.FetchNoteRequest;
import com.xhs.rewriter.web.dto.NoteRequest;
import com.xhs.rewriter.web.dto.RewriteRequest;
import org.springframework.http.HttpStatus;
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
    private final NoteRepository noteRepository;
    private final UserAccountRepository userRepository;
    private final AiRewriteService aiRewriteService;
    private final XhsFetchService xhsFetchService;
    private final CookieCryptoService cookieCryptoService;

    public NoteController(
            NoteRepository noteRepository,
            UserAccountRepository userRepository,
            AiRewriteService aiRewriteService,
            XhsFetchService xhsFetchService,
            CookieCryptoService cookieCryptoService
    ) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.aiRewriteService = aiRewriteService;
        this.xhsFetchService = xhsFetchService;
        this.cookieCryptoService = cookieCryptoService;
    }

    @GetMapping
    public List<Note> list() {
        return noteRepository.findTop30ByOrderByUpdatedAtDesc();
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
        return noteRepository.save(note);
    }

    @PostMapping("/fetch")
    public Note fetch(@RequestBody FetchNoteRequest request, Authentication authentication) {
        UserAccount user = currentUser(authentication);
        String encrypted = user.getEncryptedCookie();
        String cookie = encrypted == null ? "" : cookieCryptoService.decrypt(encrypted);
        Note note = xhsFetchService.fetchByUrl(request.getUrl(), cookie);
        return noteRepository.save(note);
    }

    @PostMapping("/{id}/analyze")
    public Note analyze(@PathVariable Long id) {
        Note note = getNote(id);
        note.setAnalysisJson(aiRewriteService.analyze(note));
        return noteRepository.save(note);
    }

    @PostMapping("/{id}/rewrite")
    public Note rewrite(@PathVariable Long id, @RequestBody RewriteRequest request) {
        Note note = getNote(id);
        if (note.getAnalysisJson() == null || note.getAnalysisJson().trim().isEmpty()) {
            note.setAnalysisJson(aiRewriteService.analyze(note));
        }
        note.setRewriteResultsJson(aiRewriteService.rewrite(note, request));
        return noteRepository.save(note);
    }

    @GetMapping("/history")
    public List<Note> history() {
        return noteRepository.findTop30ByOrderByUpdatedAtDesc();
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        noteRepository.deleteById(id);
    }

    private Note getNote(Long id) {
        return noteRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "笔记不存在"));
    }

    private UserAccount currentUser(Authentication authentication) {
        String username = authentication == null ? "" : authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }
}
