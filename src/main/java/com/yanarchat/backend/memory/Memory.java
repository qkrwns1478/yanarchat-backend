package com.yanarchat.backend.memory;

import com.yanarchat.backend.character.Character;
import com.yanarchat.backend.common.BaseEntity;
import com.yanarchat.backend.user.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.UUID;

@Entity
@Table(name = "memories")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Memory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "character_id", nullable = false)
    private Character character;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "memory_type", nullable = false, length = 20)
    private String memoryType;

    @Builder
    public Memory(Character character, User user, String content, String memoryType) {
        this.character = character;
        this.user = user;
        this.content = content;
        this.memoryType = memoryType;
    }
}
