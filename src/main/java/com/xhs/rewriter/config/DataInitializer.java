package com.xhs.rewriter.config;

import com.xhs.rewriter.domain.Note;
import com.xhs.rewriter.domain.UserAccount;
import com.xhs.rewriter.repository.NoteRepository;
import com.xhs.rewriter.repository.UserAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final java.time.format.DateTimeFormatter DATE_TIME_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final UserAccountRepository userRepository;
    private final NoteRepository noteRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserAccountRepository userRepository, NoteRepository noteRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByUsername("admin")) {
            UserAccount admin = new UserAccount();
            admin.setUsername("admin");
            admin.setDisplayName("内容运营");
            admin.setPassword(passwordEncoder.encode("admin123"));
            userRepository.save(admin);
        }

        if (noteRepository.count() == 0) {
            Note note = new Note();
            note.setTitle("夏天通勤也能清爽不脱妆");
            note.setNoteUrl("https://www.xiaohongshu.com/explore/65f1a2b3c4d5e6f7a8b9c0d1");
            note.setOriginalContent("最近试了一个很适合夏天通勤的底妆组合，早八出门到晚上回家都还算完整。重点是妆前控油、薄涂粉底、局部定妆，鼻翼和下巴要少量多次。");
            note.setFetchedAt(java.time.LocalDateTime.now().format(DATE_TIME_FORMATTER));
            note.setLikeCount(892);
            note.setCollectCount(431);
            note.setCommentCount(58);
            note.setShareCount(36);
            note.setAuthorName("运营样例");
            note.setAuthorSignature("专注小红书内容拆解与爆款文案复盘");
            note.setPublishTime(java.time.LocalDateTime.now().minusDays(2).format(DATE_TIME_FORMATTER));
            note.setLastUpdateTime(java.time.LocalDateTime.now().minusDays(1).format(DATE_TIME_FORMATTER));
            note.setTagsJson("[\"#美妆\",\"#通勤妆\",\"#底妆\"]");
            note.setImageUrlsJson("[]");
            noteRepository.save(note);
        }
    }
}
