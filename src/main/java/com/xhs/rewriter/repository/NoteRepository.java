package com.xhs.rewriter.repository;

import com.xhs.rewriter.domain.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {
    List<Note> findTop30ByOrderByUpdatedAtDesc();
}
